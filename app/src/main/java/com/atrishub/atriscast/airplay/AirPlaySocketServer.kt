package com.atrishub.atriscast.airplay

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Local AirPlay RTSP endpoint used for discovery and session negotiation. */
class AirPlaySocketServer(
    displayName: String,
    deviceId: String,
    persistentId: String,
    private val onClient: (String) -> Unit,
    private val onRequest: (String) -> Unit,
    private val onClientClosed: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val acceptExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val clientExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val infoResponder = AirPlayInfoResponder(displayName, deviceId, persistentId)
    @Volatile private var serverSocket: ServerSocket? = null

    fun start(port: Int = AirPlayProfile.AIRPLAY_PORT): Result<Unit> {
        if (!running.compareAndSet(false, true)) return Result.success(Unit)

        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        } catch (e: Exception) {
            running.set(false)
            return Result.failure(e)
        }

        serverSocket = server
        acceptExecutor.execute {
            try {
                server.use { boundServer ->
                    while (running.get()) {
                        val socket = boundServer.accept()
                        clientExecutor.execute { handle(socket) }
                    }
                }
            } catch (e: Exception) {
                if (running.get()) onError("RTSP server failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                serverSocket = null
            }
        }
        return Result.success(Unit)
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        acceptExecutor.shutdownNow()
        clientExecutor.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.tcpNoDelay = true
            client.soTimeout = 30_000
            val remote = client.inetAddress?.hostAddress ?: "Unknown sender"
            onClient(remote)

            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())

            try {
                while (running.get() && !client.isClosed) {
                    val request = RtspRequestParser.read(input) ?: break
                    onRequest("${request.method} ${request.target}")
                    output.write(responseFor(request))
                    output.flush()
                }
            } catch (_: java.net.SocketTimeoutException) {
                // Idle negotiation sockets can be closed without turning the receiver into an error state.
            } catch (e: Exception) {
                if (running.get()) onError("RTSP client error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                onClientClosed()
            }
        }
    }

    private fun responseFor(request: RtspRequest): ByteArray {
        val method = request.method.uppercase()
        val path = request.target.substringBefore('?')

        return when {
            method == "OPTIONS" -> rtspResponse(
                cSeq = request.cSeq,
                extraHeaders = listOf(
                    "Public" to "OPTIONS, GET, POST, SETUP, RECORD, SET_PARAMETER, GET_PARAMETER, TEARDOWN"
                ),
            )

            method == "GET" && path.endsWith("/info") -> rtspResponse(
                cSeq = request.cSeq,
                contentType = "application/x-apple-binary-plist",
                body = infoResponder.createResponse(),
                extraHeaders = listOf("Audio-Jack-Status" to "connected; type=digital"),
            )

            else -> rtspResponse(
                statusCode = 501,
                reason = "Not Implemented",
                cSeq = request.cSeq,
            )
        }
    }

    private fun rtspResponse(
        statusCode: Int = 200,
        reason: String = "OK",
        cSeq: String?,
        contentType: String? = null,
        body: ByteArray = ByteArray(0),
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ): ByteArray {
        val header = buildString {
            append("RTSP/1.0 $statusCode $reason\r\n")
            cSeq?.let { append("CSeq: $it\r\n") }
            append("Server: AirTunes/${AirPlayProfile.SOURCE_VERSION}\r\n")
            extraHeaders.forEach { (key, value) -> append("$key: $value\r\n") }
            contentType?.let { append("Content-Type: $it\r\n") }
            append("Content-Length: ${body.size}\r\n")
            append("\r\n")
        }.toByteArray(Charsets.ISO_8859_1)
        return header + body
    }
}

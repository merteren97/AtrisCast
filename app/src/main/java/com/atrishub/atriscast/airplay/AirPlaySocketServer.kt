package com.atrishub.atriscast.airplay

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Milestone-1 diagnostic RTSP server.
 *
 * It intentionally does not claim a complete AirPlay session. It proves that a sender which
 * discovered AtrisCast can reach TCP/7000, and captures the first protocol requests so the
 * pairing and mirroring layers can be implemented independently.
 */
class AirPlaySocketServer(
    private val onClient: (String) -> Unit,
    private val onRequest: (String) -> Unit,
    private val onClientClosed: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val acceptExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val clientExecutor: ExecutorService = Executors.newCachedThreadPool()
    @Volatile private var serverSocket: ServerSocket? = null

    fun start(port: Int = MdnsAdvertiser.AIRPLAY_PORT): Result<Unit> {
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
            client.soTimeout = 20_000
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
                // Diagnostic server closes idle handshakes cleanly.
            } catch (e: Exception) {
                if (running.get()) onError("RTSP client error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                onClientClosed()
            }
        }
    }

    private fun responseFor(request: RtspRequest): ByteArray {
        val cseq = request.cSeq?.let { "CSeq: $it\r\n" }.orEmpty()
        return when (request.method.uppercase()) {
            "OPTIONS" -> (
                "RTSP/1.0 200 OK\r\n" +
                    cseq +
                    "Server: AtrisCast/0.1\r\n" +
                    "Public: OPTIONS, GET, POST, SETUP, RECORD, SET_PARAMETER, GET_PARAMETER, TEARDOWN\r\n" +
                    "Content-Length: 0\r\n\r\n"
                ).toByteArray(Charsets.ISO_8859_1)

            else -> (
                "RTSP/1.0 501 Not Implemented\r\n" +
                    cseq +
                    "Server: AtrisCast/0.1\r\n" +
                    "Content-Length: 0\r\n\r\n"
                ).toByteArray(Charsets.ISO_8859_1)
        }
    }
}

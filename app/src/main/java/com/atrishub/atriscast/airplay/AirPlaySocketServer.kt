package com.atrishub.atriscast.airplay

import android.view.Surface
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Local AirPlay RTSP endpoint used for discovery, negotiation and transport bring-up. */
class AirPlaySocketServer(
    displayName: String,
    deviceId: String,
    persistentId: String,
    private val surfaceProvider: () -> Surface?,
    private val onClient: (String) -> Unit,
    private val onRequest: (String) -> Unit,
    private val onTransportReady: (String) -> Unit,
    private val onMirrorStarted: () -> Unit,
    private val onMediaActivity: (Long) -> Unit,
    private val onVideoFrameRendered: () -> Unit,
    private val onVideoFormat: (String) -> Unit,
    private val onVideoGeometry: (Int, Int) -> Unit,
    private val onMirrorError: (String) -> Unit,
    private val onMirrorStopped: () -> Unit,
    private val onAudioStarted: () -> Unit,
    private val onAudioError: (String) -> Unit,
    private val onClientClosed: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val acceptExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val clientExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val infoResponder = AirPlayInfoResponder(displayName, deviceId, persistentId)

    private val sessionSequence = AtomicLong(0L)
    private val activeOwnerId = AtomicLong(0L)
    private val activeStreamingOwnerId = AtomicLong(0L)
    private val activeSessions = ConcurrentHashMap<Long, AirPlaySetupSession>()
    private val activeSockets = ConcurrentHashMap<Long, Socket>()

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
        activeSessions.values.forEach { session -> runCatching { session.close() } }
        activeSockets.values.forEach { socket -> runCatching { socket.close() } }
        activeSessions.clear()
        activeSockets.clear()
        activeOwnerId.set(0L)
        activeStreamingOwnerId.set(0L)
        acceptExecutor.shutdownNow()
        clientExecutor.shutdownNow()
    }

    fun stopActiveSession() {
        val ownerId = activeOwnerId.get()
        if (ownerId == 0L) return
        activeSessions[ownerId]?.let { session -> runCatching { session.close() } }
        activeSockets[ownerId]?.let { socket -> runCatching { socket.close() } }
    }

    private fun handle(socket: Socket) {
        val sessionId = sessionSequence.incrementAndGet()
        activeSockets[sessionId] = socket

        socket.use { client ->
            client.tcpNoDelay = true
            client.keepAlive = true
            client.soTimeout = SOCKET_READ_TIMEOUT_MS
            val remote = client.inetAddress?.hostAddress ?: "Unknown sender"
            val fairPlay = FairPlayHandshake()
            val setup = AirPlaySetupSession(
                remoteAddress = client.inetAddress,
                keyMessageProvider = { fairPlay.keyMessage },
                surfaceProvider = surfaceProvider,
                onMirrorStarted = { if (activeOwnerId.get() == sessionId) onMirrorStarted() },
                onMediaActivity = { bytes -> if (activeOwnerId.get() == sessionId) onMediaActivity(bytes) },
                onVideoFrameRendered = { if (activeOwnerId.get() == sessionId) onVideoFrameRendered() },
                onVideoFormat = { format -> if (activeOwnerId.get() == sessionId) onVideoFormat(format) },
                onMirrorError = { message -> if (activeOwnerId.get() == sessionId) onMirrorError(message) },
                onMirrorStopped = { if (activeOwnerId.get() == sessionId) onMirrorStopped() },
                onVideoGeometry = { w, h -> if (activeOwnerId.get() == sessionId) onVideoGeometry(w, h) },
                onAudioStarted = { if (activeOwnerId.get() == sessionId) onAudioStarted() },
                onAudioError = { message -> if (activeOwnerId.get() == sessionId) onAudioError(message) },
            )
            activeSessions[sessionId] = setup

            // Only notify/reset UI if no active streaming session currently owns the receiver.
            // Secondary probe sockets (e.g. GET /info) must not tear down a live mirror session.
            if (activeStreamingOwnerId.get() == 0L && activeOwnerId.compareAndSet(0L, sessionId)) {
                onClient(remote)
            }

            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            var lastActivityMs = System.currentTimeMillis()

            try {
                while (running.get() && !client.isClosed) {
                    val request = try {
                        RtspRequestParser.read(input)
                    } catch (_: RtspIdleTimeoutException) {
                        val idleMs = System.currentTimeMillis() - lastActivityMs
                        // Keep the control connection open indefinitely while media transport is
                        // active; iOS may stay silent for >30s on RTSP during long mirror sessions.
                        if (setup.hasActiveSession) continue
                        if (idleMs >= IDLE_NEGOTIATION_TIMEOUT_MS) break
                        continue
                    } ?: break

                    lastActivityMs = System.currentTimeMillis()
                    if (claimsSessionOwnership(request)) {
                        claimOwnership(sessionId, remote)
                    }
                    if (!request.method.equals("SETUP", true) && activeOwnerId.get() == sessionId) {
                        onRequest(describeRequest(request))
                    }
                    output.write(responseFor(sessionId, request, fairPlay, setup))
                    output.flush()
                    if (request.method.equals("TEARDOWN", true)) break
                }
            } catch (_: java.net.SocketTimeoutException) {
                // Mid-request stall or closed negotiation socket.
            } catch (e: Exception) {
                if (running.get() && activeOwnerId.get() == sessionId) {
                    onError("RTSP client error: ${e.message ?: e.javaClass.simpleName}")
                }
            } finally {
                activeSessions.remove(sessionId)
                activeSockets.remove(sessionId)
                activeStreamingOwnerId.compareAndSet(sessionId, 0L)
                setup.close()
                if (activeOwnerId.compareAndSet(sessionId, 0L)) {
                    onClientClosed()
                }
            }
        }
    }

    private fun claimsSessionOwnership(request: RtspRequest): Boolean {
        val method = request.method.uppercase()
        val path = request.target.substringBefore('?')
        return method == "SETUP" ||
            method == "RECORD" ||
            (method == "POST" && path.endsWith("/fp-setup"))
    }

    private fun claimOwnership(sessionId: Long, remote: String) {
        val previousOwner = activeOwnerId.getAndSet(sessionId)
        if (previousOwner != sessionId) {
            if (previousOwner != 0L) {
                activeStreamingOwnerId.compareAndSet(previousOwner, 0L)
                activeSessions.remove(previousOwner)?.let { runCatching { it.close() } }
                activeSockets.remove(previousOwner)?.let { runCatching { it.close() } }
            }
            onClient(remote)
        }
    }

    private fun responseFor(
        sessionId: Long,
        request: RtspRequest,
        fairPlay: FairPlayHandshake,
        setup: AirPlaySetupSession,
    ): ByteArray {
        val method = request.method.uppercase()
        val path = request.target.substringBefore('?')
        val protocol = responseProtocol(request.protocol)

        return when {
            method == "OPTIONS" -> response(
                protocol = protocol,
                cSeq = request.cSeq,
                extraHeaders = listOf(
                    "Public" to "SETUP, RECORD, FLUSH, TEARDOWN, OPTIONS, GET_PARAMETER, SET_PARAMETER"
                ),
            )

            method == "GET" && path.endsWith("/info") -> response(
                protocol = protocol,
                cSeq = request.cSeq,
                contentType = "application/x-apple-binary-plist",
                body = infoResponder.createResponse(),
                extraHeaders = listOf("Audio-Jack-Status" to "connected; type=digital"),
            )

            method == "POST" && path.endsWith("/fp-setup") -> {
                try {
                    response(
                        protocol = protocol,
                        cSeq = request.cSeq,
                        contentType = "application/octet-stream",
                        body = fairPlay.respond(request.body),
                    )
                } catch (_: IllegalArgumentException) {
                    response(
                        protocol = protocol,
                        statusCode = 400,
                        reason = "Bad Request",
                        cSeq = request.cSeq,
                    )
                }
            }

            method == "SETUP" -> {
                try {
                    val result = setup.respond(request.body)
                    activeStreamingOwnerId.set(sessionId)
                    if (activeOwnerId.get() == sessionId) {
                        onTransportReady("SETUP • ${result.summary}")
                    }
                    response(
                        protocol = protocol,
                        cSeq = request.cSeq,
                        contentType = "application/x-apple-binary-plist",
                        body = result.body,
                        extraHeaders = listOf("Audio-Jack-Status" to "connected; type=digital"),
                    )
                } catch (e: Exception) {
                    val message = "AirPlay SETUP rejected: ${e.message ?: e.javaClass.simpleName}"
                    if (activeOwnerId.get() == sessionId) {
                        if (message.contains("audio", ignoreCase = true)) onAudioError(message) else onMirrorError(message)
                    }
                    response(
                        protocol = protocol,
                        statusCode = 400,
                        reason = "Bad Request",
                        cSeq = request.cSeq,
                    )
                }
            }

            method == "RECORD" -> {
                activeStreamingOwnerId.set(sessionId)
                response(
                    protocol = protocol,
                    cSeq = request.cSeq,
                    extraHeaders = listOf(
                        "Audio-Latency" to "11025",
                        "Audio-Jack-Status" to "connected; type=analog",
                    ),
                )
            }

            method == "FLUSH" -> response(
                protocol = protocol,
                cSeq = request.cSeq,
            )

            method == "POST" && (path.endsWith("/feedback") || path.endsWith("/audioMode")) -> response(
                protocol = protocol,
                cSeq = request.cSeq,
            )

            method == "GET_PARAMETER" || method == "SET_PARAMETER" || method == "TEARDOWN" -> response(
                protocol = protocol,
                cSeq = request.cSeq,
            )

            else -> response(
                protocol = protocol,
                statusCode = 501,
                reason = "Not Implemented",
                cSeq = request.cSeq,
            )
        }
    }

    private fun describeRequest(request: RtspRequest): String {
        val path = request.target.substringBefore('?')
        if (request.method.equals("POST", true) && path.endsWith("/fp-setup")) {
            val phase = when (request.body.size) {
                FairPlayHandshake.PHASE1_SIZE -> "phase 1"
                FairPlayHandshake.PHASE2_SIZE -> "phase 2"
                else -> "${request.body.size} B"
            }
            val version = request.body.getOrNull(4)?.let { "v${it.toInt() and 0xFF}" }
            return listOfNotNull("POST /fp-setup", phase, version).joinToString(" • ")
        }
        return "${request.method} ${request.target}"
    }

    private fun responseProtocol(value: String): String = when (value.uppercase()) {
        "HTTP/1.1" -> "HTTP/1.1"
        "HTTP/1.0" -> "HTTP/1.0"
        else -> "RTSP/1.0"
    }

    private fun response(
        protocol: String,
        statusCode: Int = 200,
        reason: String = "OK",
        cSeq: String?,
        contentType: String? = null,
        body: ByteArray = ByteArray(0),
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ): ByteArray {
        val header = buildString {
            append("$protocol $statusCode $reason\r\n")
            cSeq?.let { append("CSeq: $it\r\n") }
            append("Server: AirTunes/${AirPlayProfile.SOURCE_VERSION}\r\n")
            extraHeaders.forEach { (key, value) -> append("$key: $value\r\n") }
            contentType?.let { append("Content-Type: $it\r\n") }
            append("Content-Length: ${body.size}\r\n")
            append("\r\n")
        }.toByteArray(Charsets.ISO_8859_1)
        return header + body
    }

    companion object {
        private const val SOCKET_READ_TIMEOUT_MS = 5_000
        private const val IDLE_NEGOTIATION_TIMEOUT_MS = 45_000L
    }
}

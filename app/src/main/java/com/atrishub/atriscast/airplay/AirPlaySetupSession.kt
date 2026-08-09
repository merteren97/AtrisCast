package com.atrishub.atriscast.airplay

import android.view.Surface
import com.dd.plist.BinaryPropertyListWriter
import com.dd.plist.NSArray
import com.dd.plist.NSData
import com.dd.plist.NSDictionary
import com.dd.plist.NSNumber
import com.dd.plist.PropertyListParser
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Per-control-connection AirPlay SETUP transport and media state. */
class AirPlaySetupSession(
    private val remoteAddress: InetAddress,
    private val keyMessageProvider: () -> ByteArray?,
    private val surfaceProvider: () -> Surface?,
    private val onMirrorStarted: () -> Unit,
    private val onMediaActivity: (Long) -> Unit,
    private val onVideoFrameRendered: () -> Unit,
    private val onVideoFormat: (String) -> Unit,
    private val onMirrorError: (String) -> Unit,
    private val onMirrorStopped: () -> Unit,
) : Closeable {
    data class SetupResult(val body: ByteArray, val summary: String)

    private val running = AtomicBoolean(true)
    private val ioExecutor: ExecutorService = Executors.newCachedThreadPool()

    @Volatile private var timingSocket: DatagramSocket? = null
    @Volatile private var mirrorReceiver: MirrorStreamReceiver? = null
    @Volatile private var audioDataSocket: DatagramSocket? = null
    @Volatile private var audioControlSocket: DatagramSocket? = null
    @Volatile private var bufferedAudioServer: ServerSocket? = null

    @Volatile var encryptedStreamKey: ByteArray? = null
        private set
    @Volatile var encryptionIv: ByteArray? = null
        private set

    fun respond(body: ByteArray): SetupResult {
        require(body.isNotEmpty()) { "SETUP body is empty" }
        val root = PropertyListParser.parse(body) as? NSDictionary
            ?: throw IllegalArgumentException("SETUP body is not a dictionary plist")
        val response = NSDictionary()
        val summary = mutableListOf<String>()
        var handled = false

        val ekey = root.objectForKey("ekey") as? NSData
        val eiv = root.objectForKey("eiv") as? NSData
        if (ekey != null || eiv != null) {
            require(ekey != null && eiv != null) { "SETUP must provide ekey and eiv together" }
            require(ekey.length() == FAIRPLAY_KEY_SIZE) { "SETUP ekey must be 72 bytes" }
            require(eiv.length() == AES_IV_SIZE) { "SETUP eiv must be 16 bytes" }

            encryptedStreamKey = ekey.bytes().clone()
            encryptionIv = eiv.bytes().clone()

            val senderTimingPort = (root.objectForKey("timingPort") as? NSNumber)?.longValue()?.toInt() ?: 0
            val localTimingPort = openTimingChannel(senderTimingPort)
            response.put("eventPort", 0L)
            response.put("timingPort", localTimingPort.toLong())
            summary += "timing UDP $localTimingPort"
            handled = true
        }

        val streams = root.objectForKey("streams") as? NSArray
        if (streams != null) {
            val responseStreams = mutableListOf<NSDictionary>()
            streams.array.forEach { item ->
                val stream = item as? NSDictionary ?: return@forEach
                val type = (stream.objectForKey("type") as? NSNumber)?.longValue()?.toInt() ?: return@forEach
                when (type) {
                    STREAM_MIRROR -> {
                        val streamConnectionId = (
                            stream.objectForKey("streamConnectionID") as? NSNumber
                            )?.longValue() ?: (
                            stream.objectForKey("streamConnectionId") as? NSNumber
                            )?.longValue() ?: throw IllegalArgumentException(
                            "mirror SETUP is missing streamConnectionID"
                        )
                        val port = openMirrorDataChannel(streamConnectionId)
                        responseStreams += NSDictionary().apply {
                            put("type", STREAM_MIRROR.toLong())
                            put("dataPort", port.toLong())
                        }
                        summary += "mirror TCP $port"
                        handled = true
                    }
                    STREAM_AUDIO -> {
                        val (dataPort, controlPort) = openAudioChannels()
                        responseStreams += NSDictionary().apply {
                            put("type", STREAM_AUDIO.toLong())
                            put("dataPort", dataPort.toLong())
                            put("controlPort", controlPort.toLong())
                        }
                        summary += "audio UDP $dataPort/$controlPort"
                        handled = true
                    }
                    STREAM_BUFFERED_AUDIO -> {
                        val port = openBufferedAudioChannel()
                        responseStreams += NSDictionary().apply {
                            put("type", STREAM_BUFFERED_AUDIO.toLong())
                            put("dataPort", port.toLong())
                        }
                        summary += "buffered TCP $port"
                        handled = true
                    }
                }
            }
            val array = NSArray(responseStreams.size)
            responseStreams.forEachIndexed { index, value -> array.setValue(index, value) }
            response.put("streams", array)
        }

        require(handled) { "SETUP plist did not contain a supported transport section" }
        return SetupResult(
            body = BinaryPropertyListWriter.writeToArray(response),
            summary = summary.joinToString(" • ").ifBlank { "transport ready" },
        )
    }

    private fun openTimingChannel(senderPort: Int): Int {
        timingSocket?.let { return it.localPort }
        val socket = DatagramSocket(0).apply { soTimeout = 700 }
        timingSocket = socket
        ioExecutor.execute {
            val response = ByteArray(128)
            while (running.get() && !socket.isClosed) {
                if (senderPort > 0) {
                    runCatching {
                        val request = timingRequest()
                        socket.send(DatagramPacket(request, request.size, remoteAddress, senderPort))
                        val packet = DatagramPacket(response, response.size)
                        socket.receive(packet)
                    }
                }
                try {
                    Thread.sleep(TIMING_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@execute
                }
            }
        }
        return socket.localPort
    }

    private fun openMirrorDataChannel(streamConnectionId: Long): Int {
        mirrorReceiver?.let { return it.dataPort }
        val encryptedKey = encryptedStreamKey
            ?: throw IllegalStateException("mirror SETUP arrived before the encrypted FairPlay key")
        val keyMessage = keyMessageProvider()
            ?: throw IllegalStateException("mirror SETUP arrived before fp-setup phase 2 completed")
        val fairPlayKey = FairPlayNative.decryptSessionKey(keyMessage, encryptedKey).getOrElse { cause ->
            throw IllegalStateException(
                "could not decrypt the AirPlay session key: ${cause.message ?: cause.javaClass.simpleName}",
                cause,
            )
        }

        val receiver = MirrorStreamReceiver(
            fairPlayKey = fairPlayKey,
            streamConnectionId = streamConnectionId,
            surfaceProvider = surfaceProvider,
            onStarted = onMirrorStarted,
            onMediaActivity = onMediaActivity,
            onFrameRendered = onVideoFrameRendered,
            onFormat = onVideoFormat,
            onError = onMirrorError,
            onStopped = onMirrorStopped,
        )
        mirrorReceiver = receiver
        receiver.start()
        return receiver.dataPort
    }

    private fun openAudioChannels(): Pair<Int, Int> {
        if (audioDataSocket == null) {
            audioDataSocket = DatagramSocket(0).also { startUdpDrain(it) }
        }
        if (audioControlSocket == null) {
            audioControlSocket = DatagramSocket(0).also { startUdpDrain(it) }
        }
        return audioDataSocket!!.localPort to audioControlSocket!!.localPort
    }

    private fun openBufferedAudioChannel(): Int {
        bufferedAudioServer?.let { return it.localPort }
        val server = ServerSocket(0).apply {
            reuseAddress = true
            soTimeout = 1_000
        }
        bufferedAudioServer = server
        ioExecutor.execute {
            while (running.get() && !server.isClosed) {
                try {
                    server.accept().use { client ->
                        client.soTimeout = 2_000
                        val buffer = ByteArray(32 * 1024)
                        while (running.get() && !client.isClosed) {
                            val count = try {
                                client.getInputStream().read(buffer)
                            } catch (_: SocketTimeoutException) {
                                continue
                            }
                            if (count <= 0) break
                            onMediaActivity(count.toLong())
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Keep loop interruptible.
                } catch (_: Exception) {
                    if (!running.get()) return@execute
                }
            }
        }
        return server.localPort
    }

    private fun startUdpDrain(socket: DatagramSocket) {
        socket.soTimeout = 1_000
        ioExecutor.execute {
            val buffer = ByteArray(8 * 1024)
            while (running.get() && !socket.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    if (packet.length > 0) onMediaActivity(packet.length.toLong())
                } catch (_: SocketTimeoutException) {
                    // Keep loop interruptible.
                } catch (_: Exception) {
                    if (!running.get()) return@execute
                }
            }
        }
    }

    private fun timingRequest(): ByteArray = ByteArray(32).also { packet ->
        packet[0] = 0x80.toByte()
        packet[1] = 0xD2.toByte()
        packet[2] = 0x00
        packet[3] = 0x07
        putNtpTimestamp(packet, 24, System.currentTimeMillis())
    }

    private fun putNtpTimestamp(target: ByteArray, offset: Int, epochMillis: Long) {
        val seconds = epochMillis / 1_000L + NTP_UNIX_EPOCH_OFFSET
        val fraction = ((epochMillis % 1_000L) shl 32) / 1_000L
        val value = (seconds shl 32) or (fraction and 0xFFFF_FFFFL)
        for (i in 0 until 8) {
            target[offset + i] = ((value ushr (56 - i * 8)) and 0xFF).toByte()
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { timingSocket?.close() }
        runCatching { mirrorReceiver?.close() }
        runCatching { audioDataSocket?.close() }
        runCatching { audioControlSocket?.close() }
        runCatching { bufferedAudioServer?.close() }
        ioExecutor.shutdownNow()
    }

    companion object {
        private const val FAIRPLAY_KEY_SIZE = 72
        private const val AES_IV_SIZE = 16
        private const val STREAM_AUDIO = 96
        private const val STREAM_BUFFERED_AUDIO = 103
        private const val STREAM_MIRROR = 110
        private const val TIMING_INTERVAL_MS = 2_500L
        private const val NTP_UNIX_EPOCH_OFFSET = 2_208_988_800L
    }
}

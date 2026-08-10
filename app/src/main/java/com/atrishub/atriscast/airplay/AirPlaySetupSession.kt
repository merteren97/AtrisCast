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
    private val onVideoGeometry: (Int, Int) -> Unit = { _, _ -> },
    private val onAudioStarted: () -> Unit = {},
    private val onAudioError: (String) -> Unit = {},
    private val sessionKeyDecryptor: (ByteArray, ByteArray) -> Result<ByteArray> = { keyMessage, encryptedKey ->
        FairPlayNative.decryptSessionKey(keyMessage, encryptedKey)
    },
) : Closeable {
    data class SetupResult(val body: ByteArray, val summary: String)

    private val running = AtomicBoolean(true)
    private val ioExecutor: ExecutorService = Executors.newCachedThreadPool()

    @Volatile private var timingSocket: DatagramSocket? = null
    @Volatile private var mirrorReceiver: MirrorStreamReceiver? = null
    @Volatile private var audioReceiver: AirPlayAudioReceiver? = null
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
                        val codecType = (stream.objectForKey("ct") as? NSNumber)?.longValue()?.toInt()
                            ?: DEFAULT_AUDIO_CODEC
                        val samplesPerFrame = (stream.objectForKey("spf") as? NSNumber)?.longValue()?.toInt()
                            ?: DEFAULT_AUDIO_SPF
                        val senderControlPort = (stream.objectForKey("controlPort") as? NSNumber)
                            ?.longValue()?.toInt() ?: 0
                        val (dataPort, controlPort) = openAudioChannels(
                            codecType = codecType,
                            samplesPerFrame = samplesPerFrame,
                            senderControlPort = senderControlPort,
                        )
                        responseStreams += NSDictionary().apply {
                            put("type", STREAM_AUDIO.toLong())
                            put("dataPort", dataPort.toLong())
                            put("controlPort", controlPort.toLong())
                        }
                        summary += "audio UDP $dataPort/$controlPort • ct=$codecType • spf=$samplesPerFrame"
                        handled = true
                    }
                    STREAM_BUFFERED_AUDIO -> {
                        val port = openBufferedAudioChannel()
                        responseStreams += NSDictionary().apply {
                            put("type", STREAM_BUFFERED_AUDIO.toLong())
                            put("dataPort", port.toLong())
                        }
                        summary += "buffered audio TCP $port"
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

    /**
     * Runs the legacy AirPlay NTP exchange.
     *
     * The first request contains only the transmit timestamp. After a valid response, subsequent
     * requests echo the client's previous reference timestamp in bytes 8..15 and our local receive
     * timestamp in bytes 16..23. This mirrors the stateful exchange used by established legacy
     * receivers instead of repeatedly sending an all-zero originate/receive section.
     */
    private fun openTimingChannel(senderPort: Int): Int {
        timingSocket?.let { return it.localPort }
        val socket = DatagramSocket(0).apply { soTimeout = 700 }
        timingSocket = socket
        ioExecutor.execute {
            val response = ByteArray(128)
            var clientReferenceTimestamp: ByteArray? = null
            var previousReceiveTimeMillis: Long? = null

            while (running.get() && !socket.isClosed) {
                if (senderPort > 0) {
                    runCatching {
                        val request = timingRequest(clientReferenceTimestamp, previousReceiveTimeMillis)
                        socket.send(DatagramPacket(request, request.size, remoteAddress, senderPort))

                        val packet = DatagramPacket(response, response.size)
                        socket.receive(packet)
                        val receivedAtMillis = System.currentTimeMillis()
                        if (packet.length >= TIMING_RESPONSE_MIN_SIZE) {
                            clientReferenceTimestamp = response.copyOfRange(24, 32)
                            previousReceiveTimeMillis = receivedAtMillis
                        }
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
        val fairPlayKey = requireFairPlaySessionKey()

        val receiver = MirrorStreamReceiver(
            fairPlayKey = fairPlayKey,
            streamConnectionId = streamConnectionId,
            surfaceProvider = surfaceProvider,
            onStarted = onMirrorStarted,
            onMediaActivity = onMediaActivity,
            onFrameRendered = onVideoFrameRendered,
            onFormat = onVideoFormat,
            onGeometry = onVideoGeometry,
            onError = onMirrorError,
            onStopped = onMirrorStopped,
        )
        mirrorReceiver = receiver
        receiver.start()
        return receiver.dataPort
    }

    private fun openAudioChannels(
        codecType: Int,
        samplesPerFrame: Int,
        senderControlPort: Int,
    ): Pair<Int, Int> {
        audioReceiver?.let { return it.dataPort to it.controlPort }

        val fairPlayKey = requireFairPlaySessionKey()
        val iv = encryptionIv?.clone()
            ?: throw IllegalStateException("audio SETUP arrived before the AirPlay encryption IV")
        val receiver = AirPlayAudioReceiver(
            fairPlayKey = fairPlayKey,
            encryptionIv = iv,
            codecType = codecType,
            samplesPerFrame = samplesPerFrame,
            remoteAddress = remoteAddress,
            remoteControlPort = senderControlPort,
            onMediaActivity = onMediaActivity,
            onStarted = onAudioStarted,
            onError = onAudioError,
        )
        audioReceiver = receiver
        receiver.start()
        return receiver.dataPort to receiver.controlPort
    }

    private fun requireFairPlaySessionKey(): ByteArray {
        val encryptedKey = encryptedStreamKey
            ?: throw IllegalStateException("media SETUP arrived before the encrypted FairPlay key")
        val keyMessage = keyMessageProvider()
            ?: throw IllegalStateException("media SETUP arrived before fp-setup phase 2 completed")
        return sessionKeyDecryptor(keyMessage, encryptedKey).getOrElse { cause ->
            throw IllegalStateException(
                "could not decrypt the AirPlay session key: ${cause.message ?: cause.javaClass.simpleName}",
                cause,
            )
        }
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

    private fun timingRequest(
        clientReferenceTimestamp: ByteArray?,
        previousReceiveTimeMillis: Long?,
    ): ByteArray = ByteArray(32).also { packet ->
        packet[0] = 0x80.toByte()
        packet[1] = 0xD2.toByte()
        packet[2] = 0x00
        packet[3] = 0x07

        if (clientReferenceTimestamp?.size == 8) {
            clientReferenceTimestamp.copyInto(packet, destinationOffset = 8)
        }
        previousReceiveTimeMillis?.let { putNtpTimestamp(packet, 16, it) }
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
        runCatching { audioReceiver?.close() }
        runCatching { bufferedAudioServer?.close() }
        ioExecutor.shutdownNow()
    }

    companion object {
        private const val FAIRPLAY_KEY_SIZE = 72
        private const val AES_IV_SIZE = 16
        private const val STREAM_AUDIO = 96
        private const val STREAM_BUFFERED_AUDIO = 103
        private const val STREAM_MIRROR = 110
        private const val DEFAULT_AUDIO_CODEC = 8
        private const val DEFAULT_AUDIO_SPF = 480
        private const val TIMING_INTERVAL_MS = 3_000L
        private const val TIMING_RESPONSE_MIN_SIZE = 32
        private const val NTP_UNIX_EPOCH_OFFSET = 2_208_988_800L
    }
}

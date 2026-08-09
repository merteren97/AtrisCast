package com.atrishub.atriscast.airplay

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Receives the legacy AirPlay RTP audio stream used alongside type-110 screen mirroring.
 *
 * iOS mirroring normally negotiates AAC-ELD (ct=8) at 44.1 kHz. AirPlay encrypts only the
 * complete 16-byte AES-CBC blocks in each RTP payload and leaves the trailing bytes unchanged.
 * The CBC IV is reset for every packet.
 */
class AirPlayAudioReceiver(
    fairPlayKey: ByteArray,
    encryptionIv: ByteArray,
    private val codecType: Int,
    private val samplesPerFrame: Int,
    @Suppress("unused") private val remoteAddress: InetAddress,
    @Suppress("unused") private val remoteControlPort: Int,
    private val onMediaActivity: (Long) -> Unit,
    private val onStarted: () -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(true)
    private val started = AtomicBoolean(false)
    private val worker: ExecutorService = Executors.newFixedThreadPool(2)
    private val dataSocket = DatagramSocket(0).apply { soTimeout = SOCKET_TIMEOUT_MS }
    private val controlSocket = DatagramSocket(0).apply { soTimeout = SOCKET_TIMEOUT_MS }
    private val sessionKey = fairPlayKey.clone()
    private val iv = encryptionIv.clone()

    val dataPort: Int
        get() = dataSocket.localPort

    val controlPort: Int
        get() = controlSocket.localPort

    init {
        require(sessionKey.size == AES_KEY_SIZE) { "AirPlay audio session key must be 16 bytes" }
        require(iv.size == AES_BLOCK_SIZE) { "AirPlay audio IV must be 16 bytes" }
    }

    fun start() {
        worker.execute(::audioLoop)
        worker.execute(::controlLoop)
    }

    private fun audioLoop() {
        if (codecType != CODEC_AAC_ELD) {
            onError("AirPlay selected unsupported audio codec ct=$codecType; expected AAC-ELD (ct=8)")
            drainDataSocket()
            return
        }

        val decoder = runCatching { AacEldDecoder(samplesPerFrame, ::markStarted) }
            .getOrElse { cause ->
                onError("Could not initialize AAC-ELD audio decoder: ${cause.message ?: cause.javaClass.simpleName}")
                drainDataSocket()
                return
            }

        val packetBuffer = ByteArray(MAX_PACKET_SIZE)
        var baseRtpTimestamp: Long? = null
        var lastPresentationTimeUs = -1L
        var lastSequence = -1

        try {
            while (running.get() && !dataSocket.isClosed) {
                val packet = DatagramPacket(packetBuffer, packetBuffer.size)
                try {
                    dataSocket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    decoder.pumpOutput()
                    continue
                }

                if (packet.length <= RTP_HEADER_SIZE) continue
                onMediaActivity(packet.length.toLong())

                val offset = packet.offset
                val version = (packetBuffer[offset].toInt() ushr 6) and 0x03
                val payloadType = packetBuffer[offset + 1].toInt() and 0x7F
                if (version != RTP_VERSION || payloadType != RTP_PAYLOAD_TYPE) continue

                val sequence = bigEndianShort(packetBuffer, offset + 2)
                if (sequence == lastSequence) continue
                lastSequence = sequence

                val rtpTimestamp = bigEndianUnsignedInt(packetBuffer, offset + 4)
                val encryptedPayload = packetBuffer.copyOfRange(
                    offset + RTP_HEADER_SIZE,
                    offset + packet.length,
                )
                val payload = AirPlayAudioCrypto.decryptPayload(sessionKey, iv, encryptedPayload)
                if (payload.isEmpty() || payload.contentEquals(NO_DATA_MARKER)) continue

                if (baseRtpTimestamp == null) baseRtpTimestamp = rtpTimestamp
                val delta = unsignedRtpDelta(baseRtpTimestamp ?: rtpTimestamp, rtpTimestamp)
                val candidatePtsUs = (delta * 1_000_000L) / SAMPLE_RATE
                val ptsUs = if (candidatePtsUs > lastPresentationTimeUs) {
                    candidatePtsUs
                } else {
                    lastPresentationTimeUs + 1L
                }
                lastPresentationTimeUs = ptsUs

                decoder.decode(payload, ptsUs)
            }
        } catch (e: Exception) {
            if (running.get()) {
                onError("AirPlay audio receiver failed: ${e.message ?: e.javaClass.simpleName}")
            }
        } finally {
            decoder.release()
        }
    }

    private fun drainDataSocket() {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (running.get() && !dataSocket.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                dataSocket.receive(packet)
                if (packet.length > 0) onMediaActivity(packet.length.toLong())
            } catch (_: SocketTimeoutException) {
                // Keep loop interruptible.
            } catch (_: Exception) {
                if (!running.get()) return
            }
        }
    }

    private fun controlLoop() {
        val buffer = ByteArray(2048)
        while (running.get() && !controlSocket.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                controlSocket.receive(packet)
                if (packet.length > 0) onMediaActivity(packet.length.toLong())
            } catch (_: SocketTimeoutException) {
                // Keep loop interruptible.
            } catch (_: Exception) {
                if (!running.get()) return
            }
        }
    }

    private fun markStarted() {
        if (started.compareAndSet(false, true)) onStarted()
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { dataSocket.close() }
        runCatching { controlSocket.close() }
        worker.shutdownNow()
    }

    private class AacEldDecoder(
        samplesPerFrame: Int,
        private val onStarted: () -> Unit,
    ) {
        private var codec: MediaCodec? = null
        private var audioTrack: AudioTrack? = null

        init {
            // AirPlay mirroring uses MPEG-4 ER AAC-ELD (Audio Object Type 39), not AAC-LD
            // (Audio Object Type 23). AOT 39 is encoded through the MPEG-4 escape form in the
            // AudioSpecificConfig. For the normal AirPlay format this produces F8 E8 50 00:
            // 44.1 kHz, stereo, 480 samples/frame, no SBR, epConfig=0.
            val audioSpecificConfig = AirPlayAacEldConfig.build(samplesPerFrame)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_AAC_ACCESS_UNIT_SIZE)
                setByteBuffer("csd-0", ByteBuffer.wrap(audioSpecificConfig))
            }
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, 0)
                start()
            }
        }

        fun decode(accessUnit: ByteArray, presentationTimeUs: Long) {
            val decoder = codec ?: return
            try {
                val inputIndex = decoder.dequeueInputBuffer(CODEC_INPUT_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val input = decoder.getInputBuffer(inputIndex)
                    if (input != null && accessUnit.size <= input.capacity()) {
                        input.clear()
                        input.put(accessUnit)
                        decoder.queueInputBuffer(inputIndex, 0, accessUnit.size, presentationTimeUs, 0)
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0)
                    }
                }
                drain(decoder, 0L)
            } catch (e: Exception) {
                throw IllegalStateException("AAC-ELD decode failed", e)
            }
        }

        fun pumpOutput() {
            codec?.let { drain(it, 0L) }
        }

        private fun drain(decoder: MediaCodec, firstTimeoutUs: Long) {
            val info = MediaCodec.BufferInfo()
            var timeoutUs = firstTimeoutUs
            while (true) {
                when (val outputIndex = decoder.dequeueOutputBuffer(info, timeoutUs)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> rebuildAudioTrack(decoder.outputFormat)
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val configBuffer = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!configBuffer && info.size > 0) {
                            val output = decoder.getOutputBuffer(outputIndex)
                            val track = audioTrack ?: rebuildAudioTrack(decoder.outputFormat)
                            if (output != null && track != null) {
                                output.position(info.offset)
                                output.limit(info.offset + info.size)
                                var remaining = info.size
                                while (remaining > 0) {
                                    val written = track.write(output, remaining, AudioTrack.WRITE_BLOCKING)
                                    if (written <= 0) break
                                    remaining -= written
                                }
                                onStarted()
                            }
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
                timeoutUs = 0L
            }
        }

        private fun rebuildAudioTrack(format: MediaFormat): AudioTrack? {
            runCatching { audioTrack?.stop() }
            runCatching { audioTrack?.release() }
            audioTrack = null

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
            val channelMask = when (channelCount) {
                1 -> AudioFormat.CHANNEL_OUT_MONO
                else -> AudioFormat.CHANNEL_OUT_STEREO
            }
            val minimumBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
            if (minimumBuffer <= 0) return null

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes((minimumBuffer * 2).coerceAtLeast(16 * 1024))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            audioTrack = track
            return track
        }

        fun release() {
            val currentCodec = codec
            codec = null
            if (currentCodec != null) {
                runCatching { currentCodec.stop() }
                runCatching { currentCodec.release() }
            }
            runCatching { audioTrack?.stop() }
            runCatching { audioTrack?.flush() }
            runCatching { audioTrack?.release() }
            audioTrack = null
        }
    }

    companion object {
        private const val CODEC_AAC_ELD = 8
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_COUNT = 2
        private const val RTP_VERSION = 2
        private const val RTP_PAYLOAD_TYPE = 96
        private const val RTP_HEADER_SIZE = 12
        private const val AES_KEY_SIZE = 16
        private const val AES_BLOCK_SIZE = 16
        private const val MAX_PACKET_SIZE = 64 * 1024
        private const val MAX_AAC_ACCESS_UNIT_SIZE = 64 * 1024
        private const val SOCKET_TIMEOUT_MS = 500
        private const val CODEC_INPUT_TIMEOUT_US = 5_000L
        private val NO_DATA_MARKER = byteArrayOf(0x00, 0x68, 0x34, 0x00)

        private fun bigEndianShort(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

        private fun bigEndianUnsignedInt(bytes: ByteArray, offset: Int): Long =
            ((bytes[offset].toLong() and 0xFFL) shl 24) or
                ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
                ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
                (bytes[offset + 3].toLong() and 0xFFL)

        private fun unsignedRtpDelta(base: Long, current: Long): Long =
            (current - base) and 0xFFFF_FFFFL
    }
}

/** Builds the codec-specific bytes Android's AAC decoder needs for the AirPlay AAC-ELD stream. */
internal object AirPlayAacEldConfig {
    fun build(samplesPerFrame: Int): ByteArray = when (samplesPerFrame) {
        // MPEG-4 ER AAC-ELD AOT 39, 44.1 kHz, stereo, frameLengthFlag=1, no SBR, epConfig=0.
        480 -> byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x50, 0x00)
        // Same profile using the standard ELD 512-sample frame length.
        512 -> byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x40, 0x00)
        else -> throw IllegalArgumentException("Unsupported AAC-ELD samples-per-frame: $samplesPerFrame")
    }
}

/** Pure AES-CBC helper kept separate so the AirPlay packet rule is unit-testable. */
internal object AirPlayAudioCrypto {
    private const val AES_BLOCK_SIZE = 16

    fun decryptPayload(key: ByteArray, iv: ByteArray, payload: ByteArray): ByteArray {
        require(key.size == AES_BLOCK_SIZE) { "AES-128 key must be 16 bytes" }
        require(iv.size == AES_BLOCK_SIZE) { "AES-CBC IV must be 16 bytes" }
        if (payload.isEmpty()) return ByteArray(0)

        val encryptedLength = (payload.size / AES_BLOCK_SIZE) * AES_BLOCK_SIZE
        if (encryptedLength == 0) return payload.clone()

        val cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }
        val decrypted = cipher.doFinal(payload, 0, encryptedLength)
        if (encryptedLength == payload.size) return decrypted

        return decrypted + payload.copyOfRange(encryptedLength, payload.size)
    }
}

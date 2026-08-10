package com.atrishub.atriscast.airplay

import android.os.Process
import android.view.Surface
import java.io.Closeable
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher

/**
 * Receives the AirPlay type-110 mirror TCP stream and feeds H.264 to Android MediaCodec.
 * Network/decryption and decoder work are deliberately separated so a short decoder stall does not
 * tear the sender's TCP stream. The queue is intentionally small and applies TCP back-pressure
 * instead of silently accumulating seconds of latency or dropping predictive H.264 frames.
 */
class MirrorStreamReceiver(
    fairPlayKey: ByteArray,
    streamConnectionId: Long,
    private val surfaceProvider: () -> Surface?,
    private val onStarted: () -> Unit,
    private val onMediaActivity: (Long) -> Unit,
    private val onFrameRendered: () -> Unit,
    private val onFormat: (String) -> Unit,
    private val onGeometry: (Int, Int) -> Unit,
    private val onError: (String) -> Unit,
    private val onStopped: () -> Unit,
) : Closeable {
    private sealed interface WorkItem {
        data class Config(
            val sps: ByteArray,
            val pps: ByteArray,
            val codedWidth: Int,
            val codedHeight: Int,
            val visibleWidth: Int,
            val visibleHeight: Int,
        ) : WorkItem

        data class Frame(
            val annexB: ByteArray,
            val senderPresentationTimeUs: Long,
        ) : WorkItem
    }

    private val running = AtomicBoolean(true)
    private val resyncRequested = AtomicBoolean(false)
    private val server = ServerSocket(0).apply {
        reuseAddress = true
        soTimeout = 1_000
    }
    private val worker: ExecutorService = Executors.newFixedThreadPool(2)
    private val queue = ArrayBlockingQueue<WorkItem>(QUEUE_CAPACITY)
    private val cipher: Cipher = MirrorCrypto.createVideoCipher(fairPlayKey, streamConnectionId)
    @Volatile private var client: Socket? = null

    // Reader-thread state used to suppress repeated AirPlay codec packets. iOS can resend identical
    // SPS/PPS metadata (including pause/resume variants); treating every copy as a new codec epoch
    // clears valid queued frames and creates visible hitches.
    private var lastQueuedConfig: WorkItem.Config? = null

    val dataPort: Int
        get() = server.localPort

    fun start() {
        worker.execute(::readerLoop)
        worker.execute(::decoderLoop)
    }

    private fun readerLoop() {
        try {
            while (running.get() && !server.isClosed) {
                try {
                    val accepted = server.accept()
                    client = accepted
                    accepted.tcpNoDelay = true
                    accepted.soTimeout = SOCKET_TIMEOUT_MS
                    runCatching { accepted.receiveBufferSize = SOCKET_RECEIVE_BUFFER_BYTES }
                    onStarted()

                    // Do not pause the sender while the Activity/overlay Surface is being created.
                    // MirrorVideoDecoder keeps the latest IDR GOP until a valid Surface appears.
                    readClient(accepted)
                    break
                } catch (_: java.net.SocketTimeoutException) {
                    // Periodically wake so close() can terminate the accept loop.
                }
            }
        } catch (e: Exception) {
            if (running.get()) onError("Mirror socket failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            running.set(false)
            onStopped()
        }
    }

    private fun readClient(socket: Socket) {
        socket.use { connected ->
            val input = connected.getInputStream()
            val header = ByteArray(HEADER_SIZE)
            var invalidVideoPackets = 0

            while (running.get() && !connected.isClosed) {
                if (!readFully(input, header)) break
                val payloadSize = littleEndianInt(header, 0)
                val payloadType = header[4].toInt() and 0xFF
                if (payloadSize < 0 || payloadSize > MAX_PAYLOAD_SIZE) {
                    onError("Mirror packet rejected: invalid payload size $payloadSize")
                    break
                }

                val payload = ByteArray(payloadSize)
                if (payloadSize > 0 && !readFully(input, payload)) break
                onMediaActivity((HEADER_SIZE + payloadSize).toLong())

                when (payloadType) {
                    TYPE_VIDEO -> {
                        if (payload.isEmpty()) {
                            onError("AirPlay sent an empty H.264 video packet")
                            continue
                        }

                        // AES-CTR is continuous over the complete mirror stream. Every encrypted
                        // payload must therefore advance this Cipher even when a frame is rejected.
                        val decrypted = cipher.update(payload) ?: ByteArray(0)
                        val parsed = MirrorCrypto.parseAvccFrame(decrypted)
                        if (parsed == null) {
                            invalidVideoPackets++
                            if (invalidVideoPackets == INVALID_VIDEO_DIAGNOSTIC_THRESHOLD) {
                                onError(
                                    "AirPlay video data arrived, but $invalidVideoPackets consecutive " +
                                        "packets were invalid after AES-CTR decryption"
                                )
                            }
                            continue
                        }

                        invalidVideoPackets = 0
                        val senderMarksIdr = (header[5].toInt() and IDR_PACKET_FLAG) != 0
                        if (senderMarksIdr && NAL_TYPE_IDR !in parsed.nalTypes) {
                            onError("AirPlay marked a keyframe packet, but decrypted H.264 contained no IDR NAL")
                            continue
                        }
                        enqueue(
                            WorkItem.Frame(
                                annexB = parsed.annexB,
                                senderPresentationTimeUs = mirrorTimestampUs(header),
                            )
                        )
                    }

                    TYPE_CONFIG -> {
                        if (payload.isEmpty()) {
                            onError("AirPlay codec configuration was empty; H.264 was not negotiated")
                            continue
                        }
                        parseAvcConfig(payload, header)?.let(::enqueue)
                    }

                    // Type 2 can be a zero-length legacy keepalive and type 5 is a streaming report.
                    // Both are intentionally consumed so their headers cannot tear down the stream.
                    TYPE_KEEPALIVE, TYPE_REPORT -> Unit
                    else -> Unit
                }
            }
        }
    }

    private fun decoderLoop() {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) }

        val decoder = MirrorVideoDecoder(
            surfaceProvider = surfaceProvider,
            onFrameRendered = onFrameRendered,
            onFormat = onFormat,
            onError = onError,
        )
        var senderEpochUs: Long? = null
        var lastPtsUs = -1L
        var fallbackPtsUs = 0L
        var retryFrame: WorkItem.Frame? = null

        try {
            while (running.get() || queue.isNotEmpty() || retryFrame != null) {
                // MediaCodec back-pressure retries the exact same frame before consuming another
                // network item. This preserves H.264 reference order and lets the small queue push
                // back naturally through TCP instead of growing a hidden latency buffer.
                val retrying = retryFrame != null
                if (retrying && resyncRequested.get()) {
                    retryFrame = null
                    continue
                }

                val item: WorkItem? = retryFrame ?: queue.poll(DECODER_POLL_MS, TimeUnit.MILLISECONDS)
                var decoderBusy = false

                when (item) {
                    is WorkItem.Config -> {
                        retryFrame = null
                        onGeometry(item.visibleWidth, item.visibleHeight)
                        onFormat(
                            if (item.visibleWidth != item.codedWidth || item.visibleHeight != item.codedHeight) {
                                "${item.visibleWidth}x${item.visibleHeight} • coded ${item.codedWidth}x${item.codedHeight}"
                            } else {
                                "${item.visibleWidth}x${item.visibleHeight}"
                            }
                        )
                        decoder.configure(
                            item.sps,
                            item.pps,
                            item.codedWidth,
                            item.codedHeight,
                        )
                    }

                    is WorkItem.Frame -> {
                        if (!retrying && resyncRequested.getAndSet(false)) decoder.requestResync()

                        val senderTimeUs = item.senderPresentationTimeUs
                        val relativePtsUs = if (senderTimeUs > 0L) {
                            if (senderEpochUs == null) senderEpochUs = senderTimeUs
                            (senderTimeUs - (senderEpochUs ?: senderTimeUs)).coerceAtLeast(0L)
                        } else {
                            fallbackPtsUs
                        }
                        val ptsUs = if (relativePtsUs > lastPtsUs) relativePtsUs else lastPtsUs + 1L

                        if (decoder.decode(item.annexB, ptsUs)) {
                            retryFrame = null
                            lastPtsUs = ptsUs
                            fallbackPtsUs = ptsUs + FALLBACK_FRAME_INTERVAL_US
                        } else {
                            retryFrame = item
                            decoderBusy = true
                        }
                    }

                    null -> Unit
                }

                // AirPlay is change-driven: a static iPhone screen may stop sending frames after an
                // IDR. MediaCodec output becomes available asynchronously a few milliseconds later,
                // so output MUST be drained independently of new input.
                val outputWaitUs = when {
                    decoderBusy -> DECODER_BUSY_OUTPUT_WAIT_US
                    item == null -> IDLE_OUTPUT_WAIT_US
                    else -> 0L
                }
                decoder.pumpOutput(outputWaitUs)
            }

            // Give a final decoded picture a chance to reach the Surface when the sender closes
            // immediately after its last frame.
            repeat(FINAL_DRAIN_ATTEMPTS) { decoder.pumpOutput(FINAL_OUTPUT_WAIT_US) }
        } catch (e: Exception) {
            if (running.get()) onError("Mirror decoder worker failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            decoder.release()
        }
    }

    private fun enqueue(item: WorkItem) {
        if (item is WorkItem.Config) {
            val previous = lastQueuedConfig
            val sameCodec = previous?.let { hasSameCodecConfiguration(it, item) } == true
            val sameGeometry = previous?.let { hasSameGeometry(it, item) } == true

            // Exact duplicates are protocol chatter, not a new decode epoch. Ignoring them avoids the
            // queue clear + keyframe wait that previously showed up as intermittent visual glitches.
            if (sameCodec && sameGeometry) return
            lastQueuedConfig = item

            if (!sameCodec) {
                // A genuinely new SPS/PPS describes a new prediction/format epoch. Old queued frames
                // may belong to a different geometry/reference chain, so replace them with the new
                // config and wait for the IDR that AirPlay sends immediately after it.
                queue.clear()
                offerWithBackpressure(item)
                resyncRequested.set(true)
                return
            }

            // Geometry-only metadata can update the output aspect ratio without destroying valid
            // predictive frames or forcing the hardware decoder through a keyframe resync.
            offerWithBackpressure(item)
            return
        }

        offerWithBackpressure(item)
    }

    private fun offerWithBackpressure(item: WorkItem) {
        while (running.get()) {
            try {
                if (queue.offer(item, QUEUE_OFFER_WAIT_MS, TimeUnit.MILLISECONDS)) return
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun hasSameCodecConfiguration(first: WorkItem.Config, second: WorkItem.Config): Boolean =
        first.codedWidth == second.codedWidth &&
            first.codedHeight == second.codedHeight &&
            first.sps.contentEquals(second.sps) &&
            first.pps.contentEquals(second.pps)

    private fun hasSameGeometry(first: WorkItem.Config, second: WorkItem.Config): Boolean =
        first.visibleWidth == second.visibleWidth && first.visibleHeight == second.visibleHeight

    /** Parses the AVCDecoderConfigurationRecord carried in payload type 1. */
    private fun parseAvcConfig(payload: ByteArray, header: ByteArray): WorkItem.Config? {
        if (payload.size >= 8 && payload.copyOfRange(4, 8).contentEquals(HVC1_SIGNATURE)) {
            onError("iPhone selected HEVC/H.265 although AtrisCast advertises H.264-only mirroring")
            return null
        }

        return runCatching {
            require(payload.size >= 9) { "AVC config is too short" }
            val spsLength = bigEndianShort(payload, 6)
            val spsStart = 8
            val spsEnd = spsStart + spsLength
            require(spsLength > 0 && spsEnd < payload.size) { "invalid SPS length" }

            val ppsCountOffset = spsEnd
            val ppsLengthOffset = ppsCountOffset + 1
            require(ppsLengthOffset + 2 <= payload.size) { "missing PPS length" }
            val ppsLength = bigEndianShort(payload, ppsLengthOffset)
            val ppsStart = ppsLengthOffset + 2
            val ppsEnd = ppsStart + ppsLength
            require(ppsLength > 0 && ppsEnd <= payload.size) { "invalid PPS length" }

            val sps = payload.copyOfRange(spsStart, spsEnd)
            val pps = payload.copyOfRange(ppsStart, ppsEnd)
            val headerWidth = videoDimension(header, 56, AirPlayProfile.DISPLAY_WIDTH.toInt())
            val headerHeight = videoDimension(header, 60, AirPlayProfile.DISPLAY_HEIGHT.toInt())
            val spsDimensions = H264SpsParser.parseDimensions(sps)

            // Header offsets 56/60 describe the visible AirPlay picture and can legitimately be a
            // cropped width such as ~500 pixels in portrait mode. MediaCodec must be initialized
            // with the macroblock-coded SPS dimensions, while the playback Surface uses the visible
            // aspect ratio so portrait content is letterboxed rather than stretched to 16:9.
            val codedWidth = spsDimensions?.codedWidth ?: alignToMacroblock(headerWidth)
            val codedHeight = spsDimensions?.codedHeight ?: alignToMacroblock(headerHeight)
            val visibleWidth = headerWidth.takeIf { it > 0 }
                ?: spsDimensions?.visibleWidth
                ?: codedWidth
            val visibleHeight = headerHeight.takeIf { it > 0 }
                ?: spsDimensions?.visibleHeight
                ?: codedHeight

            WorkItem.Config(
                sps = sps,
                pps = pps,
                codedWidth = codedWidth,
                codedHeight = codedHeight,
                visibleWidth = visibleWidth,
                visibleHeight = visibleHeight,
            )
        }.getOrElse {
            onError("Could not parse H.264 SPS/PPS: ${it.message ?: it.javaClass.simpleName}")
            null
        }
    }

    private fun videoDimension(header: ByteArray, offset: Int, fallback: Int): Int {
        if (offset + 4 > header.size) return fallback
        val value = Float.fromBits(littleEndianInt(header, offset))
        return if (value.isFinite() && value >= MIN_VIDEO_DIMENSION && value <= MAX_VIDEO_DIMENSION) {
            value.toInt()
        } else {
            fallback
        }
    }

    /** AirPlay mirror timestamps are NTP 32.32 values stored little-endian at header bytes 8..15. */
    private fun mirrorTimestampUs(header: ByteArray): Long {
        if (header.size < 16) return 0L
        val raw = littleEndianLong(header, 8)
        if (raw == 0L) return 0L
        val seconds = raw ushr 32
        val fraction = raw and 0xFFFF_FFFFL
        return seconds * 1_000_000L + (fraction * 1_000_000L ushr 32)
    }

    private fun alignToMacroblock(value: Int): Int = ((value.coerceAtLeast(16) + 15) / 16) * 16

    private fun readFully(input: InputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size && running.get()) {
            val read = try {
                input.read(target, offset, target.size - offset)
            } catch (_: java.net.SocketTimeoutException) {
                continue
            }
            if (read < 0) return false
            if (read == 0) continue
            offset += read
        }
        return offset == target.size
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun littleEndianLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) {
            value = value or ((bytes[offset + index].toLong() and 0xFFL) shl (index * 8))
        }
        return value
    }

    private fun bigEndianShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    override fun close() {
        running.set(false)
        runCatching { client?.close() }
        runCatching { server.close() }
        queue.clear()
        worker.shutdownNow()
    }

    companion object {
        private const val HEADER_SIZE = 128
        private const val TYPE_VIDEO = 0
        private const val TYPE_CONFIG = 1
        private const val TYPE_KEEPALIVE = 2
        private const val TYPE_REPORT = 5
        private const val IDR_PACKET_FLAG = 0x10
        private const val NAL_TYPE_IDR = 5
        private const val MAX_PAYLOAD_SIZE = 8 * 1024 * 1024
        private const val QUEUE_CAPACITY = 12
        private const val QUEUE_OFFER_WAIT_MS = 20L
        private const val SOCKET_TIMEOUT_MS = 2_000
        private const val SOCKET_RECEIVE_BUFFER_BYTES = 512 * 1024
        private const val FALLBACK_FRAME_INTERVAL_US = 1_000_000L / 60L
        private const val DECODER_POLL_MS = 4L
        private const val DECODER_BUSY_OUTPUT_WAIT_US = 2_000L
        private const val IDLE_OUTPUT_WAIT_US = 2_000L
        private const val FINAL_OUTPUT_WAIT_US = 8_000L
        private const val FINAL_DRAIN_ATTEMPTS = 3
        private const val INVALID_VIDEO_DIAGNOSTIC_THRESHOLD = 3
        private const val MIN_VIDEO_DIMENSION = 16f
        private const val MAX_VIDEO_DIMENSION = 8192f
        private val HVC1_SIGNATURE = byteArrayOf(0x68, 0x76, 0x63, 0x31)
    }
}

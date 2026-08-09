package com.atrishub.atriscast.airplay

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
 * Network/decryption and decoder work are deliberately separated so a slow decoder cannot stall the
 * sender's TCP socket and grow latency without bound.
 */
class MirrorStreamReceiver(
    fairPlayKey: ByteArray,
    streamConnectionId: Long,
    private val surfaceProvider: () -> Surface?,
    private val onStarted: () -> Unit,
    private val onMediaActivity: (Long) -> Unit,
    private val onFrameRendered: () -> Unit,
    private val onFormat: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStopped: () -> Unit,
) : Closeable {
    private sealed interface WorkItem {
        data class Config(
            val sps: ByteArray,
            val pps: ByteArray,
            val width: Int,
            val height: Int,
        ) : WorkItem

        data class Frame(val annexB: ByteArray) : WorkItem
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
                    onStarted()

                    // onStarted switches Compose to MirrorPlaybackScreen, whose SurfaceView is
                    // process-local and therefore does not exist before the data connection. Give
                    // that surface a short head start so the initial SPS/PPS + IDR stays in the TCP
                    // receive buffer instead of racing the UI. MirrorVideoDecoder also buffers the
                    // GOP as a second line of defence when Activity launch is slower than this wait.
                    waitForRenderSurface()
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

    private fun waitForRenderSurface() {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SURFACE_STARTUP_GRACE_MS)
        while (running.get() && System.nanoTime() < deadlineNanos) {
            if (surfaceProvider()?.isValid == true) return
            try {
                Thread.sleep(SURFACE_POLL_MS)
            } catch (_: InterruptedException) {
                return
            }
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

                        // Always advance the AES-CTR stream for every video payload. Skipping an
                        // encrypted packet would desynchronize all following payloads.
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
                        enqueue(WorkItem.Frame(parsed.annexB))
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
        val decoder = MirrorVideoDecoder(
            surfaceProvider = surfaceProvider,
            onFrameRendered = onFrameRendered,
            onFormat = onFormat,
            onError = onError,
        )
        var ptsUs = 0L
        try {
            while (running.get() || queue.isNotEmpty()) {
                when (val item = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue) {
                    is WorkItem.Config -> decoder.configure(item.sps, item.pps, item.width, item.height)
                    is WorkItem.Frame -> {
                        if (resyncRequested.getAndSet(false)) decoder.requestResync()
                        decoder.decode(item.annexB, ptsUs)
                        ptsUs += FRAME_INTERVAL_US
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) onError("Mirror decoder worker failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            decoder.release()
        }
    }

    private fun enqueue(item: WorkItem) {
        if (queue.offer(item)) return
        queue.poll()
        queue.offer(item)
        // A dropped predictive frame invalidates references that may follow it. Keep draining the
        // socket to bound latency, but make the decoder ignore frames until the next IDR.
        resyncRequested.set(true)
    }

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

            val width = videoDimension(header, 56, AirPlayProfile.DISPLAY_WIDTH.toInt())
            val height = videoDimension(header, 60, AirPlayProfile.DISPLAY_HEIGHT.toInt())

            WorkItem.Config(
                sps = payload.copyOfRange(spsStart, spsEnd),
                pps = payload.copyOfRange(ppsStart, ppsEnd),
                width = width,
                height = height,
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
        private const val QUEUE_CAPACITY = 90
        private const val SOCKET_TIMEOUT_MS = 2_000
        private const val FRAME_INTERVAL_US = 1_000_000L / 60L
        private const val SURFACE_STARTUP_GRACE_MS = 1_500L
        private const val SURFACE_POLL_MS = 10L
        private const val INVALID_VIDEO_DIAGNOSTIC_THRESHOLD = 3
        private const val MIN_VIDEO_DIMENSION = 16f
        private const val MAX_VIDEO_DIMENSION = 8192f
        private val HVC1_SIGNATURE = byteArrayOf(0x68, 0x76, 0x63, 0x31)
    }
}

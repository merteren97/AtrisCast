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
        data class Config(val sps: ByteArray, val pps: ByteArray) : WorkItem
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
            while (running.get() && !connected.isClosed) {
                if (!readFully(input, header)) break
                val payloadSize = littleEndianInt(header, 0)
                val payloadType = littleEndianShort(header, 4) and 0xFF
                if (payloadSize <= 0 || payloadSize > MAX_PAYLOAD_SIZE) {
                    onError("Mirror packet rejected: invalid payload size $payloadSize")
                    break
                }

                val payload = ByteArray(payloadSize)
                if (!readFully(input, payload)) break
                onMediaActivity((HEADER_SIZE + payloadSize).toLong())

                when (payloadType) {
                    TYPE_VIDEO -> {
                        // Always advance the AES-CTR stream for every video payload. Skipping an
                        // encrypted packet would desynchronize all following payloads.
                        val decrypted = cipher.update(payload) ?: ByteArray(0)
                        val annexB = MirrorCrypto.avccToAnnexB(decrypted)
                        if (annexB.isNotEmpty()) enqueue(WorkItem.Frame(annexB))
                    }
                    TYPE_CONFIG -> parseAvcConfig(payload)?.let(::enqueue)
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
                    is WorkItem.Config -> decoder.configure(item.sps, item.pps)
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
    private fun parseAvcConfig(payload: ByteArray): WorkItem.Config? {
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

            WorkItem.Config(
                sps = payload.copyOfRange(spsStart, spsEnd),
                pps = payload.copyOfRange(ppsStart, ppsEnd),
            )
        }.getOrElse {
            onError("Could not parse H.264 SPS/PPS: ${it.message ?: it.javaClass.simpleName}")
            null
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

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

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
        private const val MAX_PAYLOAD_SIZE = 8 * 1024 * 1024
        private const val QUEUE_CAPACITY = 90
        private const val SOCKET_TIMEOUT_MS = 2_000
        private const val FRAME_INTERVAL_US = 1_000_000L / 60L
    }
}

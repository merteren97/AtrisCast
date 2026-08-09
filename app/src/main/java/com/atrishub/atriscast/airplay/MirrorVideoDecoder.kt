package com.atrishub.atriscast.airplay

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer
import java.util.ArrayDeque

/** Small synchronous MediaCodec adapter used from the mirror decoder worker thread. */
class MirrorVideoDecoder(
    private val surfaceProvider: () -> Surface?,
    private val onFrameRendered: () -> Unit,
    private val onFormat: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private data class PendingFrame(
        val data: ByteArray,
        val presentationTimeUs: Long,
    )

    private var codec: MediaCodec? = null
    private var configuredSurface: Surface? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var codedWidth = AirPlayProfile.DISPLAY_WIDTH.toInt()
    private var codedHeight = AirPlayProfile.DISPLAY_HEIGHT.toInt()
    private var awaitingKeyframe = true
    private val pendingUntilSurface = ArrayDeque<PendingFrame>()

    fun configure(
        newSps: ByteArray,
        newPps: ByteArray,
        width: Int = AirPlayProfile.DISPLAY_WIDTH.toInt(),
        height: Int = AirPlayProfile.DISPLAY_HEIGHT.toInt(),
    ) {
        sps = newSps.clone()
        pps = newPps.clone()
        if (width > 0) codedWidth = width
        if (height > 0) codedHeight = height
        rebuild(surfaceProvider()?.takeIf { it.isValid })
        codec?.let { flushPendingFrames(it) }
    }

    fun decode(annexB: ByteArray, presentationTimeUs: Long) {
        val liveSurface = surfaceProvider()?.takeIf { it.isValid }
        if (liveSurface !== configuredSurface) rebuild(liveSurface)

        val decoder = codec
        if (decoder == null) {
            bufferUntilSurface(annexB, presentationTimeUs)
            return
        }

        if (!flushPendingFrames(decoder)) {
            appendPendingFrame(annexB, presentationTimeUs)
            return
        }
        decodeIntoCodec(decoder, annexB, presentationTimeUs)
    }

    fun requestResync() {
        awaitingKeyframe = true
        pendingUntilSurface.clear()
    }

    fun release() {
        releaseCodec()
        pendingUntilSurface.clear()
        sps = null
        pps = null
    }

    /**
     * The mirror SurfaceView is created only after the iPhone connects its type-110 TCP stream.
     * That means the first SPS/PPS + IDR can legitimately arrive before Android has a render
     * surface. The old implementation returned immediately in that state and permanently lost the
     * initial IDR, leaving MediaCodec waiting for another keyframe that the sender might not emit
     * for a long time. Buffer from the most recent IDR until the surface exists so decoder startup
     * is independent of Activity/Compose scheduling.
     */
    private fun bufferUntilSurface(annexB: ByteArray, presentationTimeUs: Long) {
        val isIdr = containsIdr(annexB)
        if (isIdr) {
            pendingUntilSurface.clear()
            pendingUntilSurface.addLast(PendingFrame(annexB.clone(), presentationTimeUs))
            return
        }

        if (pendingUntilSurface.isEmpty()) return
        appendPendingFrame(annexB, presentationTimeUs)
    }

    private fun appendPendingFrame(annexB: ByteArray, presentationTimeUs: Long) {
        if (pendingUntilSurface.size >= MAX_PENDING_SURFACE_FRAMES) {
            // We can no longer guarantee an unbroken prediction chain. Drop the partial GOP and
            // wait for the next IDR rather than feeding MediaCodec frames with missing references.
            pendingUntilSurface.clear()
            awaitingKeyframe = true
            return
        }
        pendingUntilSurface.addLast(PendingFrame(annexB.clone(), presentationTimeUs))
    }

    /** Returns true when every buffered frame has been submitted in original decode order. */
    private fun flushPendingFrames(decoder: MediaCodec): Boolean {
        while (pendingUntilSurface.isNotEmpty()) {
            val pending = pendingUntilSurface.removeFirst()
            if (!decodeIntoCodec(decoder, pending.data, pending.presentationTimeUs)) {
                pendingUntilSurface.addFirst(pending)
                return false
            }
        }
        return true
    }

    /** Returns false only when MediaCodec temporarily has no input buffer available. */
    private fun decodeIntoCodec(
        decoder: MediaCodec,
        annexB: ByteArray,
        presentationTimeUs: Long,
    ): Boolean {
        if (awaitingKeyframe) {
            if (!containsIdr(annexB)) return true
            awaitingKeyframe = false
        }

        return try {
            val inputIndex = decoder.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIndex < 0) return false

            val input = decoder.getInputBuffer(inputIndex)
            if (input == null) {
                awaitingKeyframe = true
                return true
            }
            input.clear()
            if (annexB.size > input.remaining()) {
                onError("H.264 frame is larger than the MediaCodec input buffer")
                awaitingKeyframe = true
                return true
            }

            input.put(annexB)
            decoder.queueInputBuffer(inputIndex, 0, annexB.size, presentationTimeUs, 0)
            drain(decoder)
            true
        } catch (e: Exception) {
            onError("Video decoder failed: ${e.message ?: e.javaClass.simpleName}")
            releaseCodec()
            awaitingKeyframe = true
            false
        }
    }

    private fun rebuild(surface: Surface?) {
        releaseCodec()
        configuredSurface = surface
        val configSps = sps ?: return
        val configPps = pps ?: return
        if (surface == null || !surface.isValid) return

        try {
            val format = MediaFormat.createVideoFormat(MIME_AVC, codedWidth, codedHeight).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(MirrorCrypto.withStartCode(configSps)))
                setByteBuffer("csd-1", ByteBuffer.wrap(MirrorCrypto.withStartCode(configPps)))
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
            }
            codec = MediaCodec.createDecoderByType(MIME_AVC).apply {
                configure(format, surface, null, 0)
                start()
            }
            awaitingKeyframe = true
        } catch (e: Exception) {
            onError("Could not initialize H.264 decoder: ${e.message ?: e.javaClass.simpleName}")
            releaseCodec()
        }
    }

    private fun drain(decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            when (val outputIndex = decoder.dequeueOutputBuffer(info, 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = decoder.outputFormat
                    val width = outputFormat.getInteger(MediaFormat.KEY_WIDTH)
                    val height = outputFormat.getInteger(MediaFormat.KEY_HEIGHT)
                    onFormat("${width}x$height")
                }
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outputIndex >= 0) {
                    decoder.releaseOutputBuffer(outputIndex, true)
                    onFrameRendered()
                }
            }
        }
    }

    private fun releaseCodec() {
        val current = codec
        codec = null
        configuredSurface = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.release() }
        }
    }

    private fun containsIdr(data: ByteArray): Boolean {
        var index = 0
        while (index + 4 < data.size) {
            val threeByte = data[index] == 0.toByte() && data[index + 1] == 0.toByte() && data[index + 2] == 1.toByte()
            val fourByte = index + 4 < data.size && data[index] == 0.toByte() && data[index + 1] == 0.toByte() &&
                data[index + 2] == 0.toByte() && data[index + 3] == 1.toByte()
            if (threeByte) {
                if ((data[index + 3].toInt() and 0x1F) == 5) return true
                index += 3
            } else if (fourByte) {
                if ((data[index + 4].toInt() and 0x1F) == 5) return true
                index += 4
            } else {
                index++
            }
        }
        return false
    }

    companion object {
        private const val MIME_AVC = "video/avc"
        private const val INPUT_TIMEOUT_US = 5_000L
        private const val MAX_INPUT_SIZE = 4 * 1024 * 1024
        private const val MAX_PENDING_SURFACE_FRAMES = 120
    }
}

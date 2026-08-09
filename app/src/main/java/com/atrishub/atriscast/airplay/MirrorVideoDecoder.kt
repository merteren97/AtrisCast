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
    private var parameterSets: ByteArray? = null
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
        parameterSets = MirrorCrypto.withStartCode(newSps) + MirrorCrypto.withStartCode(newPps)
        if (width > 0) codedWidth = width
        if (height > 0) codedHeight = height
        rebuild(surfaceProvider()?.takeIf { it.isValid })
        codec?.let { flushPendingFrames(it) }
    }

    fun decode(annexB: ByteArray, presentationTimeUs: Long) {
        val decoder = ensureDecoderForCurrentSurface()
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

    /**
     * Pumps MediaCodec even when AirPlay has stopped sending input because the iPhone screen is
     * static. Decoder output is asynchronous and can become ready after the last input packet.
     */
    fun pumpOutput(timeoutUs: Long = 0L) {
        val decoder = ensureDecoderForCurrentSurface() ?: return
        if (!flushPendingFrames(decoder)) return
        drain(decoder, timeoutUs)
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
        parameterSets = null
    }

    /**
     * The playback Surface may appear after the initial SPS/PPS + IDR. Keep the GOP beginning at
     * the most recent IDR so Compose/Activity scheduling cannot permanently lose decoder startup.
     */
    private fun bufferUntilSurface(annexB: ByteArray, presentationTimeUs: Long) {
        val isIdr = containsNalType(annexB, NAL_TYPE_IDR)
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
        val isIdr = containsNalType(annexB, NAL_TYPE_IDR)
        if (awaitingKeyframe) {
            if (!isIdr) return true
            awaitingKeyframe = false
        }

        // UxPlay prepends the unencrypted SPS/PPS packet to the encrypted IDR that immediately
        // follows it. MediaCodec accepts csd-0/csd-1, but a number of Android TV hardware decoders
        // are more reliable when parameter sets are also present in-band at the random access point.
        val sample = if (isIdr && !containsNalType(annexB, NAL_TYPE_SPS)) {
            (parameterSets ?: ByteArray(0)) + annexB
        } else {
            annexB
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
            if (sample.size > input.remaining()) {
                onError("H.264 frame is larger than the MediaCodec input buffer")
                awaitingKeyframe = true
                return true
            }

            input.put(sample)
            val flags = if (isIdr) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            decoder.queueInputBuffer(inputIndex, 0, sample.size, presentationTimeUs, flags)
            drain(decoder, 0L)
            true
        } catch (e: Exception) {
            onError("Video decoder failed: ${e.message ?: e.javaClass.simpleName}")
            releaseCodec()
            awaitingKeyframe = true
            false
        }
    }

    private fun ensureDecoderForCurrentSurface(): MediaCodec? {
        val liveSurface = surfaceProvider()?.takeIf { it.isValid }
        if (liveSurface !== configuredSurface) rebuild(liveSurface)
        return codec
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
                setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            }
            awaitingKeyframe = true
        } catch (e: Exception) {
            onError("Could not initialize H.264 decoder: ${e.message ?: e.javaClass.simpleName}")
            releaseCodec()
        }
    }

    private fun drain(decoder: MediaCodec, firstTimeoutUs: Long) {
        val info = MediaCodec.BufferInfo()
        var timeoutUs = firstTimeoutUs
        while (true) {
            when (val outputIndex = decoder.dequeueOutputBuffer(info, timeoutUs)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(describeOutputFormat(decoder.outputFormat))
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outputIndex >= 0) {
                    val codecConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    val decodeOnly = (info.flags and MediaCodec.BUFFER_FLAG_DECODE_ONLY) != 0
                    val render = !codecConfig && !decodeOnly
                    decoder.releaseOutputBuffer(outputIndex, render)
                    if (render) onFrameRendered()
                }
            }
            // Only the first dequeue may wait. Once one output event was observed, drain everything
            // already available without blocking so the decoder worker stays low latency.
            timeoutUs = 0L
        }
    }

    private fun describeOutputFormat(format: MediaFormat): String {
        val codedW = format.getInteger(MediaFormat.KEY_WIDTH)
        val codedH = format.getInteger(MediaFormat.KEY_HEIGHT)
        val hasCrop = format.containsKey("crop-left") && format.containsKey("crop-right") &&
            format.containsKey("crop-top") && format.containsKey("crop-bottom")
        if (!hasCrop) return "${codedW}x$codedH"

        val cropLeft = format.getInteger("crop-left")
        val cropRight = format.getInteger("crop-right")
        val cropTop = format.getInteger("crop-top")
        val cropBottom = format.getInteger("crop-bottom")
        val visibleW = cropRight - cropLeft + 1
        val visibleH = cropBottom - cropTop + 1
        return if (visibleW > 0 && visibleH > 0 && (visibleW != codedW || visibleH != codedH)) {
            "${visibleW}x$visibleH • coded ${codedW}x$codedH"
        } else {
            "${codedW}x$codedH"
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

    private fun containsNalType(data: ByteArray, expectedType: Int): Boolean {
        var index = 0
        while (index + 3 < data.size) {
            val threeByte = data[index] == 0.toByte() && data[index + 1] == 0.toByte() && data[index + 2] == 1.toByte()
            val fourByte = index + 4 < data.size && data[index] == 0.toByte() && data[index + 1] == 0.toByte() &&
                data[index + 2] == 0.toByte() && data[index + 3] == 1.toByte()
            if (threeByte) {
                if ((data[index + 3].toInt() and 0x1F) == expectedType) return true
                index += 3
            } else if (fourByte) {
                if ((data[index + 4].toInt() and 0x1F) == expectedType) return true
                index += 4
            } else {
                index++
            }
        }
        return false
    }

    companion object {
        private const val MIME_AVC = "video/avc"
        private const val NAL_TYPE_IDR = 5
        private const val NAL_TYPE_SPS = 7
        private const val INPUT_TIMEOUT_US = 5_000L
        private const val MAX_INPUT_SIZE = 4 * 1024 * 1024
        private const val MAX_PENDING_SURFACE_FRAMES = 120
    }
}

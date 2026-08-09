package com.atrishub.atriscast.airplay

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer

/** Small synchronous MediaCodec adapter used from the mirror decoder worker thread. */
class MirrorVideoDecoder(
    private val surfaceProvider: () -> Surface?,
    private val onFrameRendered: () -> Unit,
    private val onFormat: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var codec: MediaCodec? = null
    private var configuredSurface: Surface? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var awaitingKeyframe = true

    fun configure(newSps: ByteArray, newPps: ByteArray) {
        sps = newSps.clone()
        pps = newPps.clone()
        rebuild(surfaceProvider())
    }

    fun decode(annexB: ByteArray, presentationTimeUs: Long) {
        val liveSurface = surfaceProvider()?.takeIf { it.isValid }
        if (liveSurface !== configuredSurface) rebuild(liveSurface)
        val decoder = codec ?: return

        if (awaitingKeyframe) {
            if (!containsIdr(annexB)) return
            awaitingKeyframe = false
        }

        try {
            val inputIndex = decoder.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIndex >= 0) {
                decoder.getInputBuffer(inputIndex)?.let { input ->
                    input.clear()
                    if (annexB.size > input.remaining()) {
                        onError("H.264 frame is larger than the MediaCodec input buffer")
                        awaitingKeyframe = true
                        return
                    }
                    input.put(annexB)
                    decoder.queueInputBuffer(inputIndex, 0, annexB.size, presentationTimeUs, 0)
                }
            }
            drain(decoder)
        } catch (e: Exception) {
            onError("Video decoder failed: ${e.message ?: e.javaClass.simpleName}")
            releaseCodec()
            awaitingKeyframe = true
        }
    }

    fun requestResync() {
        awaitingKeyframe = true
    }

    fun release() {
        releaseCodec()
        sps = null
        pps = null
    }

    private fun rebuild(surface: Surface?) {
        releaseCodec()
        configuredSurface = surface
        val configSps = sps ?: return
        val configPps = pps ?: return
        if (surface == null || !surface.isValid) return

        try {
            val format = MediaFormat.createVideoFormat(MIME_AVC, AirPlayProfile.DISPLAY_WIDTH.toInt(), AirPlayProfile.DISPLAY_HEIGHT.toInt()).apply {
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
    }
}

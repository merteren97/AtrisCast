package com.atrishub.atriscast.ui

import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.atrishub.atriscast.receiver.MirrorSurfaceRegistry
import com.atrishub.atriscast.receiver.ReceiverState

/** Dedicated full-screen rendering destination while an AirPlay mirror session is active. */
@Composable
fun MirrorPlaybackScreen(state: ReceiverState) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val visibleWidth = state.videoWidth?.takeIf { it > 0 }
        val visibleHeight = state.videoHeight?.takeIf { it > 0 }
        val videoAspectRatio = if (visibleWidth != null && visibleHeight != null) {
            visibleWidth.toFloat() / visibleHeight.toFloat()
        } else {
            null
        }
        val containerAspectRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
        val surfaceModifier = when {
            videoAspectRatio == null -> Modifier.fillMaxSize()
            videoAspectRatio >= containerAspectRatio -> Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
            else -> Modifier.fillMaxHeight().aspectRatio(videoAspectRatio)
        }

        AndroidView(
            modifier = surfaceModifier.align(Alignment.Center),
            factory = {
                SurfaceView(context).apply {
                    keepScreenOn = true
                    // Keep the dedicated Surface opaque and let the black parent provide the
                    // letterbox/pillarbox bars. The AndroidView itself is constrained to the
                    // sender's visible aspect ratio, so MediaCodec SCALE_TO_FIT no longer stretches
                    // portrait iPhone frames to the TV's 16:9 dimensions.
                    setZOrderOnTop(false)
                    holder.setFormat(PixelFormat.OPAQUE)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        private var attachedSurface: Surface? = null

                        override fun surfaceCreated(holder: SurfaceHolder) {
                            attachedSurface = holder.surface
                            MirrorSurfaceRegistry.attachActivity(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            attachedSurface = holder.surface
                            MirrorSurfaceRegistry.attachActivity(holder.surface)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            attachedSurface?.let(MirrorSurfaceRegistry::detachActivity)
                            attachedSurface = null
                        }
                    })
                }
            },
        )

        if (state.videoFramesRendered == 0L || state.videoError != null) {
            val startupDetail = state.videoError ?: when {
                state.mediaBytesReceived <= 0L -> "Mirror connected • waiting for the first video packet"
                else -> "Receiving ${formatMediaBytes(state.mediaBytesReceived)} • decoding H.264"
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xD9161E24), RoundedCornerShape(18.dp))
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = if (state.videoError == null) "AirPlay screen is starting…" else "AirPlay video error",
                    color = Color.White,
                    fontSize = 18.sp,
                )
                Text(
                    text = startupDetail,
                    color = if (state.videoError == null) Color(0xFF95A6B2) else Color(0xFFFFA69E),
                    fontSize = 12.sp,
                )
                state.videoResolution?.let {
                    Text(text = it, color = Color(0xFF42E8D2), fontSize = 11.sp)
                }
                state.audioError?.let {
                    Text(text = "Audio: $it", color = Color(0xFFFFC27A), fontSize = 11.sp)
                }
            }
        }
    }
}

private fun formatMediaBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB AirPlay data", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KB AirPlay data", bytes / 1024.0)
    else -> "$bytes B AirPlay data"
}

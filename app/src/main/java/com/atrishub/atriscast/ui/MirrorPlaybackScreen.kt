package com.atrishub.atriscast.ui

import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.atrishub.atriscast.receiver.MirrorSurfaceRegistry
import com.atrishub.atriscast.receiver.ReceiverState
import kotlinx.coroutines.delay

/** Dedicated full-screen rendering destination while an AirPlay mirror session is active. */
@Composable
internal fun MirrorPlaybackScreen(
    state: ReceiverState,
    text: UiStrings,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    var fillScreen by remember { mutableStateOf(MirrorSurfaceRegistry.fillScreen) }
    var showStats by remember { mutableStateOf(false) }
    var osdVisible by remember { mutableStateOf(true) }
    var osdPulse by remember { mutableStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val disconnectFocus = remember { FocusRequester() }

    LaunchedEffect(fillScreen) {
        MirrorSurfaceRegistry.fillScreen = fillScreen
    }

    // Auto-hide the OSD a few seconds after interaction so playback stays clean edge-to-edge.
    LaunchedEffect(osdVisible, osdPulse, state.videoFramesRendered) {
        if (osdVisible && state.videoFramesRendered > 0L && state.videoError == null) {
            delay(OSD_AUTO_HIDE_MS)
            osdVisible = false
        }
    }

    LaunchedEffect(osdVisible) {
        if (osdVisible) {
            runCatching { disconnectFocus.requestFocus() }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    BackHandler(enabled = true) {
        if (osdVisible) {
            osdVisible = false
        } else {
            osdVisible = true
            osdPulse++
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.Menu, Key.Info,
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (!osdVisible) {
                            osdVisible = true
                            osdPulse++
                            return@onPreviewKeyEvent true
                        }
                        osdPulse++
                        false
                    }
                    else -> {
                        if (osdVisible) osdPulse++
                        false
                    }
                }
            }
    ) {
        val visibleWidth = state.videoWidth?.takeIf { it > 0 }
        val visibleHeight = state.videoHeight?.takeIf { it > 0 }
        val videoAspectRatio = if (visibleWidth != null && visibleHeight != null) {
            visibleWidth.toFloat() / visibleHeight.toFloat()
        } else {
            null
        }
        val containerAspectRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
        val surfaceModifier = when {
            fillScreen || videoAspectRatio == null -> Modifier.fillMaxSize()
            videoAspectRatio >= containerAspectRatio -> Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
            else -> Modifier.fillMaxHeight().aspectRatio(videoAspectRatio)
        }

        AndroidView(
            modifier = surfaceModifier.align(Alignment.Center),
            factory = {
                SurfaceView(context).apply {
                    keepScreenOn = true
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
                state.mediaBytesReceived <= 0L -> text.mirrorWaitingPacket
                else -> String.format(text.mirrorDecodingHint, formatMediaBytes(state.mediaBytesReceived))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xE0101820), RoundedCornerShape(20.dp))
                    .border(1.dp, BorderStrong, RoundedCornerShape(20.dp))
                    .padding(horizontal = 32.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (state.videoError == null) text.mirrorStartingTitle else text.mirrorErrorTitle,
                    color = PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = startupDetail,
                    color = if (state.videoError == null) SecondaryText else ErrorCoral,
                    fontSize = 14.sp,
                )
                state.videoResolution?.let {
                    Text(text = it, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                state.audioError?.let {
                    Text(text = "${text.mirrorAudioPrefix}: $it", color = WarningAmber, fontSize = 13.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = showStats && state.videoFramesRendered > 0L,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .background(Color(0xCC0B1219), RoundedCornerShape(14.dp))
                    .border(1.dp, BorderStrong, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "${state.videoResolution ?: "H.264"} • ${state.videoFramesRendered} fps/f",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${formatMediaBytes(state.mediaBytesReceived)} • ${
                        if (state.audioActive) text.audioOn else text.audioWaiting
                    }",
                    color = SecondaryText,
                    fontSize = 12.sp,
                )
            }
        }

        AnimatedVisibility(
            visible = osdVisible && state.videoFramesRendered > 0L,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(28.dp)) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0xD90C141D), RoundedCornerShape(999.dp))
                        .border(1.dp, BorderStrong, RoundedCornerShape(999.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "AirPlay • ${state.advertisedName}",
                        color = PrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.videoResolution?.let {
                        Text(text = it, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    state.remoteAddress?.let {
                        Text(text = it, color = SecondaryText, fontSize = 13.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color(0xE60B131B), RoundedCornerShape(20.dp))
                        .border(1.dp, BorderStrong, RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvActionButton(
                            label = if (fillScreen) text.mirrorAspectFill else text.mirrorAspectFit,
                            onClick = {
                                fillScreen = !fillScreen
                                osdPulse++
                            },
                            primary = false,
                        )
                        TvActionButton(
                            label = if (showStats) text.mirrorStatsOn else text.mirrorStatsOff,
                            onClick = {
                                showStats = !showStats
                                osdPulse++
                            },
                            primary = false,
                        )
                        TvActionButton(
                            label = text.mirrorBackHide,
                            onClick = { osdVisible = false },
                            primary = false,
                        )
                        TvActionButton(
                            label = text.mirrorDisconnect,
                            onClick = onDisconnect,
                            modifier = Modifier.focusRequester(disconnectFocus),
                            primary = true,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = text.mirrorPressCenterHint,
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

private const val OSD_AUTO_HIDE_MS = 4_000L

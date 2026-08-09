package com.atrishub.atriscast.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import com.atrishub.atriscast.receiver.ReceiverPhase

internal val AppBackground = Color(0xFF070B10)
internal val SideRail = Color(0xFF090F15)
internal val Surface = Color(0xFF0E151D)
internal val SurfaceRaised = Color(0xFF111B24)
internal val SurfaceSoft = Color(0xFF0B1219)
internal val Border = Color(0xFF1A2933)
internal val Accent = Color(0xFF57E2C4)
internal val AccentBlue = Color(0xFF80BFFF)
internal val PrimaryText = Color(0xFFF2F7F5)
internal val SecondaryText = Color(0xFF98A6B1)
internal val MutedText = Color(0xFF647380)

internal fun phaseColor(phase: ReceiverPhase): Color = when (phase) {
    ReceiverPhase.ADVERTISING -> Accent
    ReceiverPhase.CLIENT_CONNECTED -> AccentBlue
    ReceiverPhase.PERMISSION_REQUIRED -> Color(0xFFFFD27D)
    ReceiverPhase.ERROR -> Color(0xFFFF8F98)
    else -> Color(0xFF8795A0)
}

@Composable
fun AtrisCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

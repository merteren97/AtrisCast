package com.atrishub.atriscast.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.atrishub.atriscast.receiver.ReceiverPhase

internal val AppBackground = Color(0xFF070C12)
internal val AppBackgroundElevated = Color(0xFF0B131C)
internal val SideRail = Color(0xFF091018)
internal val Surface = Color(0xFF0F1822)
internal val SurfaceRaised = Color(0xFF15222F)
internal val SurfaceFocused = Color(0xFF1A2D3B)
internal val Border = Color(0xFF1D2E3B)
internal val BorderStrong = Color(0xFF2B4354)
internal val Accent = Color(0xFF57E2C4)
internal val AccentContainer = Color(0xFF133136)
internal val AccentBlue = Color(0xFF7CC4FF)
internal val WarningAmber = Color(0xFFFFD27D)
internal val ErrorCoral = Color(0xFFFF8F98)
internal val PrimaryText = Color(0xFFF3F8F7)
internal val SecondaryText = Color(0xFFAEBCC7)
internal val MutedText = Color(0xFF7D8F9E)

internal fun phaseColor(phase: ReceiverPhase): Color = when (phase) {
    ReceiverPhase.ADVERTISING -> Accent
    ReceiverPhase.CLIENT_CONNECTED -> AccentBlue
    ReceiverPhase.PERMISSION_REQUIRED -> WarningAmber
    ReceiverPhase.ERROR -> ErrorCoral
    else -> MutedText
}

private val AtrisCastColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF061613),
    primaryContainer = AccentContainer,
    onPrimaryContainer = PrimaryText,
    secondary = AccentBlue,
    onSecondary = Color(0xFF061522),
    background = AppBackground,
    onBackground = PrimaryText,
    surface = Surface,
    onSurface = PrimaryText,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = SecondaryText,
    border = Border,
    error = ErrorCoral,
    onError = Color(0xFF28080C),
)

private val AtrisCastTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AtrisCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AtrisCastColorScheme,
        typography = AtrisCastTypography,
        content = content,
    )
}

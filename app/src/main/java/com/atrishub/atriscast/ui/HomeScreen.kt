package com.atrishub.atriscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.atrishub.atriscast.receiver.LocalNetworkPermission
import com.atrishub.atriscast.receiver.OverlayPermissionHelper
import com.atrishub.atriscast.receiver.ReceiverPhase
import com.atrishub.atriscast.receiver.ReceiverState

@Composable
internal fun HomeScreen(
    state: ReceiverState,
    text: UiStrings,
    canDrawOverlays: Boolean,
    onPermission: () -> Unit,
    onEnableOverlay: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onRestart: () -> Unit,
) {
    val context = LocalContext.current
    val stage = toUiStage(state.protocolStage)
    val needsLocalNetwork = !LocalNetworkPermission.isGranted(context) && LocalNetworkPermission.isRequired()
    val overlaySettingsSupported = OverlayPermissionHelper.canOpenOverlaySettings(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF11232E), Color(0xFF0E1821), Color(0xFF0C141C))
                    ),
                    RoundedCornerShape(20.dp),
                )
                .border(1.dp, BorderStrong, RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1.45f)) {
                    Text(
                        text = heroEyebrow(state.phase, text),
                        color = phaseColor(state.phase),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = heroTitle(state.phase, text),
                        color = PrimaryText,
                        fontSize = 25.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = heroBody(state.phase, text),
                        color = SecondaryText,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    ProgressLine(stage = stage, text = text)
                }

                ReceiverPreviewCard(
                    state = state,
                    text = text,
                    modifier = Modifier.weight(0.72f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepGuideCard(text.step1Title, text.step1Desc, Modifier.weight(1f))
            StepGuideCard(text.step2Title, text.step2Desc, Modifier.weight(1f))
            StepGuideCard(text.step3Title, "${text.step3Desc} (${state.advertisedName})", Modifier.weight(1f))
        }

        if (!canDrawOverlays && !needsLocalNetwork) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarningAmber.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = text.backgroundLaunchTitle,
                            color = WarningAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (overlaySettingsSupported) text.backgroundLaunchHint else text.backgroundLaunchUnsupported,
                            color = SecondaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (overlaySettingsSupported) {
                        Spacer(Modifier.width(14.dp))
                        TvActionButton(
                            label = text.enableBackgroundLaunch,
                            onClick = onEnableOverlay,
                            primary = true,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                needsLocalNetwork -> TvActionButton(
                    label = text.allowNetwork,
                    onClick = onPermission,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                state.phase == ReceiverPhase.ERROR || state.phase == ReceiverPhase.STOPPED -> TvActionButton(
                    label = text.restartReceiver,
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                else -> QuickActionTile(
                    title = text.renameTv,
                    subtitle = state.advertisedName,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            }

            QuickActionTile(
                title = text.openDiagnostics,
                subtitle = stageTitle(stage, text),
                onClick = onOpenDiagnostics,
                modifier = Modifier.weight(1f),
            )

            QuickActionTile(
                title = text.restartReceiver,
                subtitle = state.localAddress ?: localizeNetworkLabel(state.networkLabel, text),
                onClick = onRestart,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReceiverPreviewCard(state: ReceiverState, text: UiStrings, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF0A1219), RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .aspectRatio(16f / 9f)
                .background(Color(0xFF121E28), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF284350), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .background(Accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.width(6.dp).height(20.dp).background(Accent, RoundedCornerShape(3.dp)))
                    Box(Modifier.width(6.dp).height(14.dp).background(AccentBlue, RoundedCornerShape(3.dp)))
                    Box(Modifier.width(6.dp).height(9.dp).background(Color(0xFF4F8E9B), RoundedCornerShape(3.dp)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(state.advertisedName, color = PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(text.localPrivate, color = MutedText, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun StepGuideCard(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Surface, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(
            text = description,
            color = SecondaryText,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        containerColor = Surface,
        focusedColor = SurfaceFocused,
        padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { focused ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = if (focused) Color.White else PrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = if (focused) Accent else MutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressLine(stage: ProtocolStage, text: UiStrings) {
    val items = listOf(
        ProtocolStage.DISCOVERY to text.discoveryShort,
        ProtocolStage.NEGOTIATION to text.negotiateShort,
        ProtocolStage.FAIRPLAY to text.secureShort,
        ProtocolStage.TRANSPORT to text.transportShort,
        ProtocolStage.STREAMING to text.streamShort,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { index, item ->
            val active = stage >= item.first
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .width(24.dp)
                        .height(4.dp)
                        .background(if (active) Accent else Color(0xFF253540), RoundedCornerShape(9.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.second,
                    color = if (active) SecondaryText else MutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            if (index != items.lastIndex) {
                Box(Modifier.width(10.dp).height(1.dp).background(Color(0xFF253540)))
            }
        }
    }
}

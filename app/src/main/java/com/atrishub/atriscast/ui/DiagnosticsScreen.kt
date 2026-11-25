package com.atrishub.atriscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.atrishub.atriscast.airplay.AirPlayProfile
import com.atrishub.atriscast.receiver.ReceiverPhase
import com.atrishub.atriscast.receiver.ReceiverState

@Composable
internal fun DiagnosticsScreen(
    state: ReceiverState,
    text: UiStrings,
    onRestart: () -> Unit,
    onToggleService: () -> Unit,
) {
    val stage = toUiStage(state.protocolStage)
    val isRunning = state.phase != ReceiverPhase.STOPPED

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1.0f)
                .background(Surface, RoundedCornerShape(20.dp))
                .border(1.dp, Border, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text(text.sessionProgress, color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text.sessionProgressHelp, color = SecondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))

            StageRow("01", text.discovery, stage >= ProtocolStage.DISCOVERY)
            StageRow("02", text.capabilities, stage >= ProtocolStage.NEGOTIATION)
            StageRow("03", text.fairPlay, stage >= ProtocolStage.FAIRPLAY)
            StageRow("04", text.transport, stage >= ProtocolStage.TRANSPORT)
            StageRow("05", text.media, stage >= ProtocolStage.STREAMING)

            Spacer(Modifier.height(12.dp))
            Text(text.latestRequest.uppercase(), color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.lastRequest ?: text.waitingForSender,
                color = AccentBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val activeError = state.error ?: state.videoError ?: state.audioError
            activeError?.let {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorCoral.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .border(1.dp, ErrorCoral.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(it, color = ErrorCoral, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1.0f)
                .background(Surface, RoundedCornerShape(20.dp))
                .border(1.dp, Border, RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text.receiverDetails, color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailTile(text.receiver, state.advertisedName, Modifier.weight(1f))
                DetailTile(
                    text.network,
                    localizeNetworkLabel(state.networkLabel, text),
                    Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailTile(text.localAddress, state.localAddress ?: text.waiting, Modifier.weight(1f))
                DetailTile(text.endpoint, "TCP ${AirPlayProfile.AIRPLAY_PORT}", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailTile(text.sender, state.remoteAddress ?: state.lastSenderAddress ?: text.none, Modifier.weight(1f))
                DetailTile(text.videoStream, state.videoResolution ?: text.none, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailTile(
                    text.dataReceived,
                    "${formatMediaBytes(state.mediaBytesReceived)} • ${state.videoFramesRendered}f",
                    Modifier.weight(1f),
                )
                DetailTile(
                    text.audioStream,
                    when {
                        state.audioError != null -> state.audioError
                        state.audioActive -> text.audioOn
                        else -> text.audioWaiting
                    },
                    Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvActionButton(
                    label = text.restartReceiver,
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                TvActionButton(
                    label = if (isRunning) text.stopReceiver else text.startReceiver,
                    onClick = onToggleService,
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
            }
        }
    }
}

@Composable
private fun StageRow(number: String, label: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(28.dp)
                .background(if (active) Accent.copy(alpha = 0.14f) else Color(0xFF121C25), RoundedCornerShape(8.dp))
                .border(1.dp, if (active) Accent.copy(alpha = 0.38f) else Border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = if (active) Accent else MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (active) PrimaryText else MutedText,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DetailTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SurfaceRaised, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = MutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = PrimaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

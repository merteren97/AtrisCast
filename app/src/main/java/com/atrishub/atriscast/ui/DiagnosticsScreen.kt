package com.atrishub.atriscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.atrishub.atriscast.airplay.AirPlayProfile
import com.atrishub.atriscast.receiver.ReceiverState

@Composable
internal fun DiagnosticsScreen(state: ReceiverState, text: UiStrings, onRestart: () -> Unit) {
    val stage = protocolStage(state.lastRequest)
    Row(
        modifier = Modifier.fillMaxWidth().height(535.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .background(Surface, RoundedCornerShape(25.dp))
                .border(1.dp, Border, RoundedCornerShape(25.dp))
                .padding(28.dp)
        ) {
            Column {
                Text(text.sessionProgress, color = PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text(text.sessionProgressHelp, color = SecondaryText, fontSize = 12.sp)
                Spacer(Modifier.height(24.dp))

                StageRow("01", text.discovery, stage >= ProtocolStage.DISCOVERY)
                StageRow("02", text.capabilities, stage >= ProtocolStage.NEGOTIATION)
                StageRow("03", text.fairPlay, stage >= ProtocolStage.FAIRPLAY)
                StageRow("04", text.transport, stage >= ProtocolStage.TRANSPORT)
                StageRow("05", text.media, stage >= ProtocolStage.STREAMING)

                Spacer(Modifier.height(24.dp))
                Text(text.latestRequest.uppercase(), color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(
                    state.lastRequest ?: text.waitingForSender,
                    color = AccentBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                state.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Color(0xFFFF9DA4), fontSize = 11.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .background(Surface, RoundedCornerShape(25.dp))
                .border(1.dp, Border, RoundedCornerShape(25.dp))
                .padding(28.dp)
        ) {
            Column {
                Text(text.receiverDetails, color = PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(24.dp))
                Detail(text.receiver, state.advertisedName)
                Detail(text.network, state.networkLabel)
                Detail(text.localAddress, state.localAddress ?: text.waiting)
                Detail(text.endpoint, "TCP ${AirPlayProfile.AIRPLAY_PORT}")
                Detail(text.profile, "${AirPlayProfile.MODEL} • ${AirPlayProfile.SOURCE_VERSION}")
                Detail(text.sender, state.remoteAddress ?: text.none)
                Spacer(Modifier.weight(1f))
                ProductButton(text.restartReceiver, onRestart)
            }
        }
    }
}

@Composable
private fun StageRow(number: String, label: String, active: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(32.dp)
                .background(if (active) Accent.copy(alpha = 0.11f) else Color(0xFF121A22), RoundedCornerShape(10.dp))
                .border(1.dp, if (active) Accent.copy(alpha = 0.32f) else Color(0xFF1D2831), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = if (active) Accent else MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Text(label, color = if (active) PrimaryText else MutedText, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Text(label.uppercase(), color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(value, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    Spacer(Modifier.height(16.dp))
}

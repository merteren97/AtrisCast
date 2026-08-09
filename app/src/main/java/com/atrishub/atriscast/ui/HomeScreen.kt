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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.atrishub.atriscast.receiver.LocalNetworkPermission
import com.atrishub.atriscast.receiver.ReceiverPhase
import com.atrishub.atriscast.receiver.ReceiverState

@Composable
internal fun HomeScreen(state: ReceiverState, text: UiStrings, onPermission: () -> Unit, onRestart: () -> Unit) {
    val stage = protocolStage(state.lastRequest)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF102029), Color(0xFF0D171F), Color(0xFF0C131A))),
                RoundedCornerShape(28.dp),
            )
            .border(1.dp, Color(0xFF20343D), RoundedCornerShape(28.dp))
            .padding(34.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(modifier = Modifier.weight(1.35f).fillMaxHeight()) {
                Text(
                    heroEyebrow(state.phase, text),
                    color = phaseColor(state.phase),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(15.dp))
                Text(heroTitle(state.phase, text), color = PrimaryText, fontSize = 35.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(heroBody(state.phase, text), color = SecondaryText, fontSize = 16.sp, lineHeight = 24.sp)

                Spacer(Modifier.weight(1f))
                Text(text.currentStep.uppercase(), color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(stageTitle(stage, text), color = AccentBlue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                ProgressLine(stage, text)
            }

            Spacer(Modifier.width(42.dp))
            ReceiverVisual(state, text, Modifier.weight(0.75f).fillMaxHeight())
        }
    }

    Spacer(Modifier.height(20.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoTile(text.receiver, state.advertisedName, text.airplayName, Modifier.weight(1f))
        InfoTile(text.network, state.networkLabel, state.localAddress ?: text.waiting, Modifier.weight(1f))
        InfoTile(text.session, stageTitle(stage, text), state.lastRequest ?: text.waitingForSender, Modifier.weight(1f))
    }

    Spacer(Modifier.height(17.dp))
    when {
        !LocalNetworkPermission.isGranted(LocalContext.current) && LocalNetworkPermission.isRequired() -> ProductButton(text.allowNetwork, onPermission)
        state.phase == ReceiverPhase.ERROR -> ProductButton(text.restartReceiver, onRestart)
    }
}

@Composable
private fun ReceiverVisual(state: ReceiverState, text: UiStrings, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF0A1117), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF1D3038), RoundedCornerShape(24.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(238.dp)
                    .height(142.dp)
                    .background(Color(0xFF111C24), RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0xFF27404A), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(58.dp)
                        .background(Accent.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                        .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.width(7.dp).height(27.dp).background(Accent, RoundedCornerShape(6.dp)))
                        Box(Modifier.width(7.dp).height(20.dp).background(AccentBlue, RoundedCornerShape(6.dp)))
                        Box(Modifier.width(7.dp).height(14.dp).background(Color(0xFF4D8792), RoundedCornerShape(6.dp)))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(54.dp).height(5.dp).background(Color(0xFF263641), RoundedCornerShape(9.dp)))
            Spacer(Modifier.height(22.dp))
            Text(state.advertisedName, color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(text.localPrivate, color = MutedText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ProgressLine(stage: ProtocolStage, text: UiStrings) {
    val items = listOf(
        ProtocolStage.DISCOVERY to text.discoveryShort,
        ProtocolStage.NEGOTIATION to text.negotiateShort,
        ProtocolStage.FAIRPLAY to text.secureShort,
        ProtocolStage.STREAMING to text.streamShort,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { index, item ->
            val active = stage >= item.first
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .width(25.dp)
                        .height(5.dp)
                        .background(if (active) Accent else Color(0xFF26343D), RoundedCornerShape(9.dp))
                )
                Spacer(Modifier.height(5.dp))
                Text(item.second, color = if (active) SecondaryText else MutedText, fontSize = 9.sp)
            }
            if (index != items.lastIndex) {
                Box(Modifier.width(16.dp).height(1.dp).background(Color(0xFF26343D)))
            }
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, helper: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(112.dp)
            .background(Surface, RoundedCornerShape(19.dp))
            .border(1.dp, Border, RoundedCornerShape(19.dp))
            .padding(horizontal = 20.dp, vertical = 17.dp)
    ) {
        Column {
            Text(label.uppercase(), color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(value, color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(helper, color = SecondaryText, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun ProductButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF163037),
            contentColor = Accent,
            focusedContainerColor = Accent,
            focusedContentColor = Color(0xFF07110F),
            pressedContainerColor = Color(0xFF45CBB0),
            pressedContentColor = Color(0xFF07110F),
            disabledContainerColor = Color(0xFF142126),
            disabledContentColor = MutedText,
        ),
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

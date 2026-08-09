package com.atrishub.atriscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.atrishub.atriscast.receiver.ReceiverPreferences

@Composable
internal fun SettingsScreen(
    text: UiStrings,
    language: String,
    startOnBoot: Boolean,
    receiverName: String,
    onLanguage: (String) -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(535.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsPanel(Modifier.weight(1f).fillMaxHeight(), text.interfaceLanguage, text.languageHelp) {
            SelectionCard(
                title = "English",
                subtitle = text.englishHelp,
                selected = language == ReceiverPreferences.LANGUAGE_ENGLISH,
            ) { onLanguage(ReceiverPreferences.LANGUAGE_ENGLISH) }
            Spacer(Modifier.height(13.dp))
            SelectionCard(
                title = "Türkçe",
                subtitle = text.turkishHelp,
                selected = language == ReceiverPreferences.LANGUAGE_TURKISH,
            ) { onLanguage(ReceiverPreferences.LANGUAGE_TURKISH) }
        }

        SettingsPanel(Modifier.weight(1f).fillMaxHeight(), text.receiverBehavior, text.behaviorHelp) {
            Text(text.startOnBoot.uppercase(), color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactChoice(text.on, startOnBoot) { onStartOnBoot(true) }
                CompactChoice(text.off, !startOnBoot) { onStartOnBoot(false) }
            }

            Spacer(Modifier.height(28.dp))
            Text(text.receiverName.uppercase(), color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(receiverName, color = PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(text.receiverNameHelp, color = SecondaryText, fontSize = 11.sp, lineHeight = 17.sp)

            Spacer(Modifier.weight(1f))
            ProductButton(text.restartReceiver, onRestart)
        }
    }
}

@Composable
private fun SettingsPanel(modifier: Modifier, title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier
            .background(Surface, RoundedCornerShape(25.dp))
            .border(1.dp, Border, RoundedCornerShape(25.dp))
            .padding(28.dp)
    ) {
        Column {
            Text(title, color = PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(body, color = SecondaryText, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(30.dp))
            content()
        }
    }
}

@Composable
private fun SelectionCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color(0xFF13282B) else SurfaceRaised,
            contentColor = if (selected) Accent else PrimaryText,
            focusedContainerColor = Color(0xFF1C353A),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF173036),
            pressedContentColor = Color.White,
            disabledContainerColor = SurfaceRaised,
            disabledContentColor = MutedText,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(if (selected) "✓  $title" else title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = if (selected) Color(0xFFAED8CE) else MutedText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CompactChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color(0xFF13282B) else Color(0xFF141C24),
            contentColor = if (selected) Accent else SecondaryText,
            focusedContainerColor = Color(0xFF1C353A),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF173036),
            pressedContentColor = Color.White,
            disabledContainerColor = Color(0xFF141C24),
            disabledContentColor = MutedText,
        ),
    ) { Text(if (selected) "✓ $label" else label, fontSize = 12.sp) }
}

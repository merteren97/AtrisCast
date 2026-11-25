package com.atrishub.atriscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.atrishub.atriscast.receiver.ReceiverPreferences

@Composable
internal fun SettingsScreen(
    text: UiStrings,
    language: String,
    startOnBoot: Boolean,
    autoOpenOnCast: Boolean,
    receiverName: String,
    canDrawOverlays: Boolean,
    overlaySettingsAvailable: Boolean,
    isRunning: Boolean,
    onLanguage: (String) -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
    onAutoOpenOnCast: (Boolean) -> Unit,
    onSaveReceiverName: (String) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRestart: () -> Unit,
    onToggleService: () -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsPanel(
            modifier = Modifier.weight(1f),
            title = text.receiverIdentity,
            body = text.receiverNameHelp,
        ) {
            Text(text.receiverName.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            TvFocusCard(
                onClick = { showRenameDialog = true },
                modifier = Modifier.fillMaxWidth(),
                containerColor = SurfaceRaised,
                padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) { focused ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(receiverName, color = PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(text.tapToRename, color = if (focused) Accent else MutedText, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(text.renameTv, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(text.autoOpenOnCast.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(text.autoOpenOnCastHelp, color = SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleChoice(text.on, autoOpenOnCast, Modifier.weight(1f)) { onAutoOpenOnCast(true) }
                ToggleChoice(text.off, !autoOpenOnCast, Modifier.weight(1f)) { onAutoOpenOnCast(false) }
            }

            Spacer(Modifier.height(12.dp))
            Text(text.backgroundLaunchTitle.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val overlayStatus = when {
                canDrawOverlays -> text.backgroundLaunchGranted
                overlaySettingsAvailable -> text.backgroundLaunchMissing
                else -> text.backgroundLaunchUnsupported
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = overlayStatus,
                    color = if (canDrawOverlays) Accent else WarningAmber,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (!canDrawOverlays && overlaySettingsAvailable) {
                    Spacer(Modifier.width(10.dp))
                    TvActionButton(
                        label = text.enableBackgroundLaunch,
                        onClick = onOpenOverlaySettings,
                        primary = true,
                    )
                }
            }
        }

        SettingsPanel(
            modifier = Modifier.weight(1f),
            title = text.receiverBehavior,
            body = text.behaviorHelp,
        ) {
            Text(text.interfaceLanguage.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionCard(
                    title = "English",
                    subtitle = text.englishHelp,
                    selected = language == ReceiverPreferences.LANGUAGE_ENGLISH,
                    modifier = Modifier.weight(1f),
                ) { onLanguage(ReceiverPreferences.LANGUAGE_ENGLISH) }
                SelectionCard(
                    title = "Türkçe",
                    subtitle = text.turkishHelp,
                    selected = language == ReceiverPreferences.LANGUAGE_TURKISH,
                    modifier = Modifier.weight(1f),
                ) { onLanguage(ReceiverPreferences.LANGUAGE_TURKISH) }
            }

            Spacer(Modifier.height(14.dp))
            Text(text.startOnBoot.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleChoice(text.on, startOnBoot, Modifier.weight(1f)) { onStartOnBoot(true) }
                ToggleChoice(text.off, !startOnBoot, Modifier.weight(1f)) { onStartOnBoot(false) }
            }

            Spacer(Modifier.height(16.dp))
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

    if (showRenameDialog) {
        RenameReceiverDialog(
            currentName = receiverName,
            text = text,
            onDismiss = { showRenameDialog = false },
            onSave = { newName ->
                showRenameDialog = false
                onSaveReceiverName(newName)
            },
        )
    }
}

@Composable
private fun SettingsPanel(
    modifier: Modifier,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(Surface, RoundedCornerShape(20.dp))
            .border(1.dp, Border, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(title, color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(body, color = SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SelectionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TvFocusCard(
        onClick = onClick,
        selected = selected,
        modifier = modifier,
        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) { focused ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (selected) "✓ $title" else title,
                color = if (focused) Color.White else if (selected) Accent else PrimaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                color = if (selected) SecondaryText else MutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToggleChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TvFocusCard(
        onClick = onClick,
        selected = selected,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        padding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    ) { focused ->
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = if (selected) "✓ $label" else label,
                color = if (focused) Color.White else if (selected) Accent else SecondaryText,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RenameReceiverDialog(
    currentName: String,
    text: UiStrings,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(currentName) { mutableStateOf(currentName) }
    val presets = listOf("AtrisCast", "Living Room TV", "Bedroom TV", "Oturma Odası TV")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .background(Surface, RoundedCornerShape(22.dp))
                .border(1.dp, BorderStrong, RoundedCornerShape(22.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text.renameTitle, color = PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(text.renameHint, color = SecondaryText, fontSize = 14.sp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppBackground, RoundedCornerShape(12.dp))
                    .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 32) draft = it },
                    singleLine = true,
                    textStyle = TextStyle(color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(text.renameQuickPresets, color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.take(2).forEach { preset ->
                    ToggleChoice(preset, draft == preset, Modifier.weight(1f)) { draft = preset }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.drop(2).forEach { preset ->
                    ToggleChoice(preset, draft == preset, Modifier.weight(1f)) { draft = preset }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TvActionButton(label = text.cancel, onClick = onDismiss, primary = false)
                TvActionButton(
                    label = text.save,
                    onClick = { onSave(draft.trim().ifBlank { ReceiverPreferences.DEFAULT_NAME }) },
                    primary = true,
                )
            }
        }
    }
}

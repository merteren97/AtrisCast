package com.atrishub.atriscast.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.atrishub.atriscast.BuildConfig
import com.atrishub.atriscast.R
import com.atrishub.atriscast.receiver.AtrisCastReceiverService
import com.atrishub.atriscast.receiver.LocalNetworkPermission
import com.atrishub.atriscast.receiver.ReceiverPreferences
import com.atrishub.atriscast.receiver.ReceiverRuntime
import com.atrishub.atriscast.receiver.ReceiverState

@Composable
fun AtrisCastApp() {
    val state by ReceiverRuntime.state.collectAsState()
    val context = LocalContext.current
    val preferences = remember { ReceiverPreferences(context) }
    var page by remember { mutableStateOf(AppPage.HOME) }
    var language by remember { mutableStateOf(preferences.languageCode) }
    var startOnBoot by remember { mutableStateOf(preferences.startOnBoot) }
    val text = strings(language)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) AtrisCastReceiverService.start(context) }

    LaunchedEffect(Unit) {
        if (LocalNetworkPermission.isGranted(context)) AtrisCastReceiverService.start(context)
    }

    if (state.mirrorActive) {
        MirrorPlaybackScreen(state)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(AppBackground, Color(0xFF091018), AppBackground)))
    ) {
        ProductRail(page, text, state) { page = it }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 38.dp, end = 48.dp, top = 36.dp, bottom = 28.dp)
        ) {
            ProductHeader(page, text, state)
            Spacer(Modifier.height(28.dp))

            when (page) {
                AppPage.HOME -> HomeScreen(
                    state = state,
                    text = text,
                    onPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) },
                    onRestart = { AtrisCastReceiverService.restart(context) },
                )
                AppPage.SETTINGS -> SettingsScreen(
                    text = text,
                    language = language,
                    startOnBoot = startOnBoot,
                    receiverName = preferences.displayName,
                    onLanguage = {
                        preferences.languageCode = it
                        language = preferences.languageCode
                    },
                    onStartOnBoot = {
                        preferences.startOnBoot = it
                        startOnBoot = it
                    },
                    onRestart = { AtrisCastReceiverService.restart(context) },
                )
                AppPage.DIAGNOSTICS -> DiagnosticsScreen(
                    state = state,
                    text = text,
                    onRestart = { AtrisCastReceiverService.restart(context) },
                )
            }

            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text.footer, color = Color(0xFF53616C), fontSize = 11.sp)
                Text("AtrisCast • ${BuildConfig.VERSION_NAME}", color = Color(0xFF485761), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ProductRail(page: AppPage, text: UiStrings, state: ReceiverState, onPage: (AppPage) -> Unit) {
    Column(
        modifier = Modifier
            .width(226.dp)
            .fillMaxHeight()
            .background(SideRail)
            .border(1.dp, Color(0xFF101A22))
            .padding(horizontal = 22.dp, vertical = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(44.dp)
                    .background(Color(0xFF0D171E), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF1B343C), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_atriscast_brand_mark),
                    contentDescription = "AtrisCast",
                    modifier = Modifier.width(48.dp).height(34.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("AtrisCast", color = PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("AirPlay receiver", color = MutedText, fontSize = 9.sp)
            }
        }

        Spacer(Modifier.height(52.dp))
        RailButton(text.home, page == AppPage.HOME) { onPage(AppPage.HOME) }
        Spacer(Modifier.height(10.dp))
        RailButton(text.settings, page == AppPage.SETTINGS) { onPage(AppPage.SETTINGS) }
        Spacer(Modifier.height(10.dp))
        RailButton(text.diagnostics, page == AppPage.DIAGNOSTICS) { onPage(AppPage.DIAGNOSTICS) }

        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .background(phaseColor(state.phase), RoundedCornerShape(99.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(statusLabel(state.phase, text), color = SecondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(BuildConfig.VERSION_NAME, color = MutedText, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun RailButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color(0xFF132029) else Color.Transparent,
            contentColor = if (selected) PrimaryText else SecondaryText,
            focusedContainerColor = Color(0xFF1B3037),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF17272F),
            pressedContentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MutedText,
        ),
    ) { Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun ProductHeader(page: AppPage, text: UiStrings, state: ReceiverState) {
    val title = when (page) {
        AppPage.HOME -> text.homeTitle
        AppPage.SETTINGS -> text.settingsTitle
        AppPage.DIAGNOSTICS -> text.diagnosticsTitle
    }
    val subtitle = when (page) {
        AppPage.HOME -> text.homeSubtitle
        AppPage.SETTINGS -> text.settingsSubtitle
        AppPage.DIAGNOSTICS -> text.diagnosticsSubtitle
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, color = PrimaryText, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = SecondaryText, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Text(state.advertisedName, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text.airplayName, color = MutedText, fontSize = 10.sp)
            }
            StatusPill(state, text)
        }
    }
}

@Composable
private fun StatusPill(state: ReceiverState, text: UiStrings) {
    val color = phaseColor(state.phase)
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(7.dp).height(7.dp).background(color, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(8.dp))
        Text(statusLabel(state.phase, text), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

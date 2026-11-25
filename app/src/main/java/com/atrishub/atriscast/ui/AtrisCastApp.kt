package com.atrishub.atriscast.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.tv.material3.Text
import com.atrishub.atriscast.BuildConfig
import com.atrishub.atriscast.R
import com.atrishub.atriscast.receiver.AtrisCastReceiverService
import com.atrishub.atriscast.receiver.LocalNetworkPermission
import com.atrishub.atriscast.receiver.OverlayPermissionHelper
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
    var autoOpenOnCast by remember { mutableStateOf(preferences.autoOpenOnCast) }
    var receiverName by remember { mutableStateOf(preferences.displayName) }
    var canDrawOverlays by remember { mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context)) }
    val text = strings(language)

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = OverlayPermissionHelper.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val localNetworkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) AtrisCastReceiverService.start(context) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (LocalNetworkPermission.isGranted(context)) {
            AtrisCastReceiverService.start(context)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (state.mirrorActive) {
        MirrorPlaybackScreen(
            state = state,
            text = text,
            onDisconnect = { AtrisCastReceiverService.disconnectMirror(context) },
        )
        return
    }

    BackHandler(enabled = page != AppPage.HOME) {
        page = AppPage.HOME
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(AppBackground, AppBackgroundElevated, AppBackground)
                )
            )
    ) {
        ProductRail(
            page = page,
            text = text,
            state = state,
            onPage = { page = it },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 32.dp, vertical = 18.dp)
        ) {
            ProductHeader(page = page, text = text, state = state)
            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "pageTransition",
                ) { activePage ->
                    when (activePage) {
                        AppPage.HOME -> HomeScreen(
                            state = state,
                            text = text,
                            canDrawOverlays = canDrawOverlays,
                            onPermission = {
                                if (LocalNetworkPermission.isRequired()) {
                                    localNetworkLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                                }
                            },
                            onEnableOverlay = { OverlayPermissionHelper.openOverlaySettings(context) },
                            onOpenDiagnostics = { page = AppPage.DIAGNOSTICS },
                            onOpenSettings = { page = AppPage.SETTINGS },
                            onRestart = { AtrisCastReceiverService.restart(context) },
                        )

                        AppPage.DIAGNOSTICS -> DiagnosticsScreen(
                            state = state,
                            text = text,
                            onRestart = { AtrisCastReceiverService.restart(context) },
                            onToggleService = {
                                if (state.phase == com.atrishub.atriscast.receiver.ReceiverPhase.STOPPED) {
                                    AtrisCastReceiverService.start(context)
                                } else {
                                    AtrisCastReceiverService.stop(context)
                                }
                            },
                        )

                        AppPage.SETTINGS -> SettingsScreen(
                            text = text,
                            language = language,
                            startOnBoot = startOnBoot,
                            autoOpenOnCast = autoOpenOnCast,
                            receiverName = receiverName,
                            canDrawOverlays = canDrawOverlays,
                            overlaySettingsAvailable = OverlayPermissionHelper.canOpenOverlaySettings(context),
                            isRunning = state.phase != com.atrishub.atriscast.receiver.ReceiverPhase.STOPPED,
                            onLanguage = {
                                preferences.languageCode = it
                                language = preferences.languageCode
                                AtrisCastReceiverService.refreshNotification(context)
                            },
                            onStartOnBoot = {
                                preferences.startOnBoot = it
                                startOnBoot = it
                            },
                            onAutoOpenOnCast = {
                                preferences.autoOpenOnCast = it
                                autoOpenOnCast = it
                            },
                            onSaveReceiverName = { newName ->
                                preferences.displayName = newName
                                receiverName = preferences.displayName
                                AtrisCastReceiverService.restart(context)
                            },
                            onOpenOverlaySettings = {
                                OverlayPermissionHelper.openOverlaySettings(context)
                            },
                            onRestart = { AtrisCastReceiverService.restart(context) },
                            onToggleService = {
                                if (state.phase == com.atrishub.atriscast.receiver.ReceiverPhase.STOPPED) {
                                    AtrisCastReceiverService.start(context)
                                } else {
                                    AtrisCastReceiverService.stop(context)
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text.footer, color = MutedText, fontSize = 12.sp)
                Text("AtrisCast • ${BuildConfig.VERSION_NAME}", color = MutedText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProductRail(
    page: AppPage,
    text: UiStrings,
    state: ReceiverState,
    onPage: (AppPage) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(214.dp)
            .fillMaxHeight()
            .background(SideRail)
            .border(1.dp, Border)
            .padding(horizontal = 18.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(40.dp)
                    .background(Color(0xFF0D171E), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1F3942), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_atriscast_brand_mark),
                    contentDescription = "AtrisCast",
                    modifier = Modifier.width(40.dp).height(28.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("AtrisCast", color = PrimaryText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("AirPlay TV", color = MutedText, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(34.dp))
        RailItem(text.home, page == AppPage.HOME) { onPage(AppPage.HOME) }
        Spacer(Modifier.height(10.dp))
        RailItem(text.diagnostics, page == AppPage.DIAGNOSTICS) { onPage(AppPage.DIAGNOSTICS) }
        Spacer(Modifier.height(10.dp))
        RailItem(text.settings, page == AppPage.SETTINGS) { onPage(AppPage.SETTINGS) }

        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(14.dp))
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(phaseColor(state.phase), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusLabel(state.phase, text),
                    color = phaseColor(state.phase),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                text = state.localAddress ?: localizeNetworkLabel(state.networkLabel, text),
                color = SecondaryText,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    TvFocusCard(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        containerColor = Color.Transparent,
        selectedColor = AccentContainer,
        focusedColor = SurfaceFocused,
        padding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = when {
                    focused -> Color.White
                    selected -> Accent
                    else -> SecondaryText
                },
                fontSize = 15.sp,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Accent, CircleShape)
                )
            }
        }
    }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(1.dp))
            Text(subtitle, color = SecondaryText, fontSize = 13.sp, maxLines = 1)
        }
        Spacer(Modifier.width(16.dp))
        StatusBadge(
            label = "${state.advertisedName} • ${localizeNetworkLabel(state.networkLabel, text)}",
            color = phaseColor(state.phase),
        )
    }
}

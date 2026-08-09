package com.atrishub.atriscast

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.atrishub.atriscast.airplay.AirPlayProfile
import com.atrishub.atriscast.receiver.AtrisCastReceiverService
import com.atrishub.atriscast.receiver.LocalNetworkPermission
import com.atrishub.atriscast.receiver.ReceiverPhase
import com.atrishub.atriscast.receiver.ReceiverPreferences
import com.atrishub.atriscast.receiver.ReceiverRuntime
import com.atrishub.atriscast.receiver.ReceiverState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtrisCastTheme {
                AtrisCastApp()
            }
        }
    }
}

private enum class AppPage { HOME, SETTINGS }

@Composable
private fun AtrisCastApp() {
    val state by ReceiverRuntime.state.collectAsState()
    val context = LocalContext.current
    val preferences = remember { ReceiverPreferences(context) }
    var page by remember { mutableStateOf(AppPage.HOME) }
    var languageCode by remember { mutableStateOf(preferences.languageCode) }
    var startOnBoot by remember { mutableStateOf(preferences.startOnBoot) }
    val text = strings(languageCode)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) AtrisCastReceiverService.start(context)
    }

    LaunchedEffect(Unit) {
        if (LocalNetworkPermission.isGranted(context)) {
            AtrisCastReceiverService.start(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B10))
            .padding(horizontal = 64.dp, vertical = 42.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                text = text,
                state = state,
                page = page,
                onHome = { page = AppPage.HOME },
                onSettings = { page = AppPage.SETTINGS },
            )

            Spacer(Modifier.height(34.dp))

            if (page == AppPage.HOME) {
                HomePage(
                    state = state,
                    text = text,
                    onPermission = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    },
                    onRestart = { AtrisCastReceiverService.restart(context) },
                )
            } else {
                SettingsPage(
                    text = text,
                    languageCode = languageCode,
                    startOnBoot = startOnBoot,
                    receiverName = preferences.displayName,
                    onLanguage = { code ->
                        preferences.languageCode = code
                        languageCode = preferences.languageCode
                    },
                    onStartOnBoot = { enabled ->
                        preferences.startOnBoot = enabled
                        startOnBoot = enabled
                    },
                    onRestartReceiver = { AtrisCastReceiverService.restart(context) },
                )
            }

            Spacer(Modifier.weight(1f))
            Footer(text)
        }
    }
}

@Composable
private fun TopBar(
    text: UiStrings,
    state: ReceiverState,
    page: AppPage,
    onHome: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(11.dp)
                    .height(42.dp)
                    .background(Color(0xFF53DFC3), RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    "AtrisCast",
                    color = Color(0xFFF4F8F7),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text.subtitle,
                    color = Color(0xFF84919D),
                    fontSize = 15.sp,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusPill(state.phase, text)
            if (page == AppPage.SETTINGS) {
                Button(onClick = onHome) { Text(text.home) }
            } else {
                Button(onClick = onSettings) { Text(text.settings) }
            }
        }
    }
}

@Composable
private fun StatusPill(phase: ReceiverPhase, text: UiStrings) {
    val accent = statusColor(phase)
    Row(
        modifier = Modifier
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.09f), RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .background(accent, RoundedCornerShape(99.dp))
        )
        Spacer(Modifier.width(9.dp))
        Text(statusEyebrow(phase, text), color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomePage(
    state: ReceiverState,
    text: UiStrings,
    onPermission: () -> Unit,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        HeroCard(state, text, Modifier.weight(1.6f).fillMaxHeight())
        ReceiverDetailsCard(state, text, Modifier.weight(0.9f).fillMaxHeight())
    }

    Spacer(Modifier.height(22.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepCard("01", text.discovery, text.discoveryBody, true, Modifier.weight(1f))
        StepCard("02", text.handshake, text.handshakeBody, state.lastRequest != null, Modifier.weight(1f))
        StepCard("03", text.streaming, text.streamingBody, false, Modifier.weight(1f))
    }

    Spacer(Modifier.height(22.dp))

    when {
        !LocalNetworkPermission.isGranted(LocalContext.current) && LocalNetworkPermission.isRequired() -> {
            Button(onClick = onPermission) { Text(text.allowNetwork) }
        }
        state.phase == ReceiverPhase.ERROR -> {
            Button(onClick = onRestart) { Text(text.restartReceiver) }
        }
    }
}

@Composable
private fun HeroCard(state: ReceiverState, text: UiStrings, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF111821), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF1D2A35), RoundedCornerShape(24.dp))
            .padding(32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                statusEyebrow(state.phase, text),
                color = statusColor(state.phase),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                statusTitle(state.phase, text),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                statusBody(state.phase, text),
                color = Color(0xFFA8B3BC),
                fontSize = 17.sp,
                lineHeight = 26.sp,
            )

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C1219), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(text.latestHandshake, color = Color(0xFF6F7F8D), fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            state.lastRequest ?: text.waitingForSender,
                            color = Color(0xFFE3ECE9),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    state.remoteAddress?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text.sender, color = Color(0xFF6F7F8D), fontSize = 12.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(it, color = Color(0xFF8FCBFF), fontSize = 15.sp)
                        }
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF9D9D), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ReceiverDetailsCard(state: ReceiverState, text: UiStrings, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF0E141C), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF1A2530), RoundedCornerShape(24.dp))
            .padding(28.dp)
    ) {
        Column {
            Text(text.receiver, color = Color(0xFF72818D), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(state.advertisedName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            DetailRow(text.network, state.networkLabel)
            DividerSpace()
            DetailRow(text.localAddress, state.localAddress ?: text.waiting)
            DividerSpace()
            DetailRow(text.airplayEndpoint, "TCP ${AirPlayProfile.AIRPLAY_PORT}")
            DividerSpace()
            DetailRow(text.protocolProfile, "${AirPlayProfile.MODEL} • ${AirPlayProfile.SOURCE_VERSION}")
            DividerSpace()
            DetailRow(text.sender, state.remoteAddress ?: text.none)
        }
    }
}

@Composable
private fun StepCard(number: String, title: String, body: String, active: Boolean, modifier: Modifier = Modifier) {
    val accent = if (active) Color(0xFF53DFC3) else Color(0xFF596672)
    Box(
        modifier = modifier
            .background(Color(0xFF0D131A), RoundedCornerShape(18.dp))
            .border(1.dp, if (active) Color(0xFF203A38) else Color(0xFF18212A), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(number, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = Color(0xFFE8EEEC), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(body, color = Color(0xFF778590), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun SettingsPage(
    text: UiStrings,
    languageCode: String,
    startOnBoot: Boolean,
    receiverName: String,
    onLanguage: (String) -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
    onRestartReceiver: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(510.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1.25f)
                .fillMaxHeight()
                .background(Color(0xFF111821), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF1D2A35), RoundedCornerShape(24.dp))
                .padding(30.dp)
        ) {
            Column {
                Text(text.settings, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(text.settingsDescription, color = Color(0xFF8997A2), fontSize = 15.sp)

                Spacer(Modifier.height(34.dp))
                SettingsLabel(text.language)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onLanguage(ReceiverPreferences.LANGUAGE_ENGLISH) }) {
                        Text(if (languageCode == ReceiverPreferences.LANGUAGE_ENGLISH) "✓ English" else "English")
                    }
                    Button(onClick = { onLanguage(ReceiverPreferences.LANGUAGE_TURKISH) }) {
                        Text(if (languageCode == ReceiverPreferences.LANGUAGE_TURKISH) "✓ Türkçe" else "Türkçe")
                    }
                }

                Spacer(Modifier.height(30.dp))
                SettingsLabel(text.startOnBoot)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onStartOnBoot(true) }) {
                        Text(if (startOnBoot) "✓ ${text.on}" else text.on)
                    }
                    Button(onClick = { onStartOnBoot(false) }) {
                        Text(if (!startOnBoot) "✓ ${text.off}" else text.off)
                    }
                }

                Spacer(Modifier.height(30.dp))
                SettingsLabel(text.receiverName)
                Spacer(Modifier.height(8.dp))
                Text(receiverName, color = Color(0xFFE6EEEB), fontSize = 18.sp)
                Text(text.receiverNameHint, color = Color(0xFF6F7E89), fontSize = 12.sp)
            }
        }

        Box(
            modifier = Modifier
                .weight(0.75f)
                .fillMaxHeight()
                .background(Color(0xFF0E141C), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF1A2530), RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            Column {
                Text(text.receiverControls, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(text.receiverControlsBody, color = Color(0xFF85939E), fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRestartReceiver) { Text(text.restartReceiver) }

                Spacer(Modifier.height(34.dp))
                SettingsLabel(text.about)
                Spacer(Modifier.height(10.dp))
                DetailRow(text.version, BuildConfig.VERSION_NAME)
                DividerSpace()
                DetailRow(text.privacy, text.localOnly)
                DividerSpace()
                DetailRow(text.project, "atrishub.com")
            }
        }
    }
}

@Composable
private fun SettingsLabel(value: String) {
    Text(value.uppercase(), color = Color(0xFF53DFC3), fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(label, color = Color(0xFF6F7E89), fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(value, color = Color(0xFFDDE6E3), fontSize = 16.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun DividerSpace() {
    Spacer(Modifier.height(19.dp))
}

@Composable
private fun Footer(text: UiStrings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.footer, color = Color(0xFF66737F), fontSize = 13.sp)
        Text("AtrisCast • ${BuildConfig.VERSION_NAME}", color = Color(0xFF53606B), fontSize = 12.sp)
    }
}

private data class UiStrings(
    val subtitle: String,
    val settings: String,
    val home: String,
    val ready: String,
    val connected: String,
    val actionRequired: String,
    val receiverError: String,
    val starting: String,
    val offline: String,
    val readyTitle: String,
    val connectedTitle: String,
    val permissionTitle: String,
    val errorTitle: String,
    val startingTitle: String,
    val stoppedTitle: String,
    val readyBody: String,
    val connectedBody: String,
    val permissionBody: String,
    val errorBody: String,
    val startingBody: String,
    val stoppedBody: String,
    val latestHandshake: String,
    val waitingForSender: String,
    val receiver: String,
    val network: String,
    val localAddress: String,
    val airplayEndpoint: String,
    val protocolProfile: String,
    val sender: String,
    val waiting: String,
    val none: String,
    val discovery: String,
    val discoveryBody: String,
    val handshake: String,
    val handshakeBody: String,
    val streaming: String,
    val streamingBody: String,
    val allowNetwork: String,
    val restartReceiver: String,
    val settingsDescription: String,
    val language: String,
    val startOnBoot: String,
    val on: String,
    val off: String,
    val receiverName: String,
    val receiverNameHint: String,
    val receiverControls: String,
    val receiverControlsBody: String,
    val about: String,
    val version: String,
    val privacy: String,
    val localOnly: String,
    val project: String,
    val footer: String,
)

private fun strings(languageCode: String): UiStrings =
    if (languageCode == ReceiverPreferences.LANGUAGE_TURKISH) {
        UiStrings(
            subtitle = "Google TV için açık kaynak yerel yayın alıcısı",
            settings = "Ayarlar",
            home = "Ana ekran",
            ready = "HAZIR",
            connected = "GÖNDERİCİ BAĞLI",
            actionRequired = "İŞLEM GEREKİYOR",
            receiverError = "ALICI HATASI",
            starting = "BAŞLATILIYOR",
            offline = "ÇEVRİMDIŞI",
            readyTitle = "Yayın almaya hazır",
            connectedTitle = "AirPlay oturumu görüşülüyor",
            permissionTitle = "Yerel ağ izni gerekli",
            errorTitle = "AtrisCast kontrol edilmeli",
            startingTitle = "Alıcı başlatılıyor…",
            stoppedTitle = "Alıcı durduruldu",
            readyBody = "iPhone, iPad veya Mac cihazınızda Ekran Yansıtma'yı açın. AtrisCast aynı yerel ağda otomatik olarak görünür.",
            connectedBody = "Bir Apple cihazı AtrisCast'e ulaştı. Oturum bilgileri görüşülüyor ve desteklenen AirPlay yetenekleri doğrulanıyor.",
            permissionBody = "Android 17, uygulamanın yerel ağda cihaz yayınlaması ve bağlantı kabul etmesi için açık izin ister.",
            errorBody = "Yerel alıcı temiz şekilde başlatılamadı. Ağ bağlantısını kontrol edip alıcıyı yeniden başlatın.",
            startingBody = "Yerel RTSP uç noktası açılıyor ve AirPlay keşif servisleri kaydediliyor.",
            stoppedBody = "Bu TV'yi yerel ağda görünür yapmak için AtrisCast alıcısını başlatın.",
            latestHandshake = "Son protokol isteği",
            waitingForSender = "Gönderici bekleniyor…",
            receiver = "Alıcı",
            network = "Ağ",
            localAddress = "Yerel adres",
            airplayEndpoint = "AirPlay uç noktası",
            protocolProfile = "Uyumluluk profili",
            sender = "Gönderici",
            waiting = "Bekleniyor…",
            none = "Yok",
            discovery = "Keşif",
            discoveryBody = "AtrisCast aynı Wi‑Fi ağındaki Apple cihazlarına duyurulur.",
            handshake = "Oturum görüşmesi",
            handshakeBody = "RTSP ve /info yetenek bilgileri göndericiyle paylaşılır.",
            streaming = "Ekran akışı",
            streamingBody = "Video ve ses taşıma katmanı sonraki protokol aşamasıdır.",
            allowNetwork = "Yerel ağ erişimine izin ver",
            restartReceiver = "Alıcıyı yeniden başlat",
            settingsDescription = "AtrisCast'in TV üzerindeki davranışını ve arayüz dilini yönetin.",
            language = "Dil",
            startOnBoot = "TV açıldığında başlat",
            on = "Açık",
            off = "Kapalı",
            receiverName = "Alıcı adı",
            receiverNameHint = "AirPlay listesinde bu ad görünür.",
            receiverControls = "Alıcı kontrolleri",
            receiverControlsBody = "Ağ veya keşif davranışı takılırsa yerel alıcı servisini güvenli şekilde yeniden başlatabilirsiniz.",
            about = "Hakkında",
            version = "Sürüm",
            privacy = "Gizlilik",
            localOnly = "Yalnızca yerel ağ",
            project = "Proje",
            footer = "Hesap gerektirmez • Bulut bağlantısı yok • Yerel ağda çalışır",
        )
    } else {
        UiStrings(
            subtitle = "Open-source local casting receiver for Google TV",
            settings = "Settings",
            home = "Home",
            ready = "READY",
            connected = "SENDER CONNECTED",
            actionRequired = "ACTION REQUIRED",
            receiverError = "RECEIVER ERROR",
            starting = "STARTING",
            offline = "OFFLINE",
            readyTitle = "Ready to cast",
            connectedTitle = "Negotiating AirPlay session",
            permissionTitle = "Local network permission needed",
            errorTitle = "AtrisCast needs attention",
            startingTitle = "Starting receiver…",
            stoppedTitle = "Receiver stopped",
            readyBody = "Open Screen Mirroring on your iPhone, iPad or Mac. AtrisCast automatically appears to devices on the same local network.",
            connectedBody = "An Apple device reached AtrisCast. Session information is being negotiated and the advertised AirPlay capabilities are being validated.",
            permissionBody = "Android 17 requires explicit permission before an app can advertise or accept connections on your local network.",
            errorBody = "The local receiver could not start cleanly. Check the network connection and restart the receiver.",
            startingBody = "Opening the local RTSP endpoint and registering AirPlay discovery services.",
            stoppedBody = "Start the AtrisCast receiver to make this TV discoverable on your local network.",
            latestHandshake = "Latest protocol request",
            waitingForSender = "Waiting for a sender…",
            receiver = "Receiver",
            network = "Network",
            localAddress = "Local address",
            airplayEndpoint = "AirPlay endpoint",
            protocolProfile = "Compatibility profile",
            sender = "Sender",
            waiting = "Waiting…",
            none = "None",
            discovery = "Discovery",
            discoveryBody = "AtrisCast advertises itself to Apple devices on the same Wi‑Fi network.",
            handshake = "Session negotiation",
            handshakeBody = "RTSP and /info capability data are exchanged with the sender.",
            streaming = "Screen stream",
            streamingBody = "Video and audio transport is the next protocol milestone.",
            allowNetwork = "Allow local network access",
            restartReceiver = "Restart receiver",
            settingsDescription = "Manage AtrisCast behavior on this TV and choose the interface language.",
            language = "Language",
            startOnBoot = "Start when TV boots",
            on = "On",
            off = "Off",
            receiverName = "Receiver name",
            receiverNameHint = "This is the name shown in the AirPlay list.",
            receiverControls = "Receiver controls",
            receiverControlsBody = "If network discovery gets stuck, safely restart the local receiver service without rebooting the TV.",
            about = "About",
            version = "Version",
            privacy = "Privacy",
            localOnly = "Local network only",
            project = "Project",
            footer = "No account required • No cloud connection • Runs on your local network",
        )
    }

private fun statusEyebrow(phase: ReceiverPhase, text: UiStrings) = when (phase) {
    ReceiverPhase.ADVERTISING -> text.ready
    ReceiverPhase.CLIENT_CONNECTED -> text.connected
    ReceiverPhase.PERMISSION_REQUIRED -> text.actionRequired
    ReceiverPhase.ERROR -> text.receiverError
    ReceiverPhase.STARTING -> text.starting
    ReceiverPhase.STOPPED -> text.offline
}

private fun statusTitle(phase: ReceiverPhase, text: UiStrings) = when (phase) {
    ReceiverPhase.ADVERTISING -> text.readyTitle
    ReceiverPhase.CLIENT_CONNECTED -> text.connectedTitle
    ReceiverPhase.PERMISSION_REQUIRED -> text.permissionTitle
    ReceiverPhase.ERROR -> text.errorTitle
    ReceiverPhase.STARTING -> text.startingTitle
    ReceiverPhase.STOPPED -> text.stoppedTitle
}

private fun statusBody(phase: ReceiverPhase, text: UiStrings) = when (phase) {
    ReceiverPhase.ADVERTISING -> text.readyBody
    ReceiverPhase.CLIENT_CONNECTED -> text.connectedBody
    ReceiverPhase.PERMISSION_REQUIRED -> text.permissionBody
    ReceiverPhase.ERROR -> text.errorBody
    ReceiverPhase.STARTING -> text.startingBody
    ReceiverPhase.STOPPED -> text.stoppedBody
}

private fun statusColor(phase: ReceiverPhase) = when (phase) {
    ReceiverPhase.ADVERTISING -> Color(0xFF53DFC3)
    ReceiverPhase.CLIENT_CONNECTED -> Color(0xFF8FCBFF)
    ReceiverPhase.PERMISSION_REQUIRED -> Color(0xFFFFD27D)
    ReceiverPhase.ERROR -> Color(0xFFFF8E8E)
    else -> Color(0xFF8F9BA6)
}

@Composable
private fun AtrisCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

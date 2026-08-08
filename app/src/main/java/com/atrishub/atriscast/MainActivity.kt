package com.atrishub.atriscast

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.atrishub.atriscast.receiver.AtrisCastReceiverService
import com.atrishub.atriscast.receiver.LocalNetworkPermission
import com.atrishub.atriscast.receiver.ReceiverPhase
import com.atrishub.atriscast.receiver.ReceiverRuntime
import com.atrishub.atriscast.receiver.ReceiverState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtrisCastTheme {
                AtrisCastScreen()
            }
        }
    }
}

@Composable
private fun AtrisCastScreen() {
    val state by ReceiverRuntime.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

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
            .padding(horizontal = 72.dp, vertical = 52.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrandHeader()
            Spacer(Modifier.height(44.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                ReceiverCard(state, Modifier.weight(1.45f))
                InfoCard(state, Modifier.weight(1f))
            }

            Spacer(Modifier.weight(1f))

            when {
                !LocalNetworkPermission.isGranted(context) && LocalNetworkPermission.isRequired() -> {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) {
                        Text("Allow local network access")
                    }
                }
                state.phase == ReceiverPhase.ERROR -> {
                    Button(onClick = { AtrisCastReceiverService.start(context) }) {
                        Text("Restart receiver")
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "Local-only • No AtrisHub account required • atrishub.com",
                color = Color(0xFF82909E),
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(34.dp)
                .background(Color(0xFF53DFC3), RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text("AtrisCast", color = Color(0xFFF3FAF8), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("Open-source casting receiver for Google TV", color = Color(0xFF8F9BA6), fontSize = 16.sp)
        }
    }
}

@Composable
private fun ReceiverCard(state: ReceiverState, modifier: Modifier = Modifier) {
    CardShell(modifier) {
        Text(statusEyebrow(state.phase), color = statusColor(state.phase), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Text(statusTitle(state.phase), color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text(statusBody(state), color = Color(0xFFAAB4BD), fontSize = 17.sp, lineHeight = 25.sp)
        state.lastRequest?.let {
            Spacer(Modifier.height(22.dp))
            Text("Latest handshake", color = Color(0xFF71808E), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(it, color = Color(0xFFDDE7E4), fontSize = 15.sp)
        }
        state.error?.let {
            Spacer(Modifier.height(20.dp))
            Text(it, color = Color(0xFFFFADAD), fontSize = 14.sp)
        }
    }
}

@Composable
private fun InfoCard(state: ReceiverState, modifier: Modifier = Modifier) {
    CardShell(modifier) {
        Text("Receiver", color = Color(0xFF71808E), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text(state.advertisedName, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(28.dp))
        LabelValue("Network", state.networkLabel)
        Spacer(Modifier.height(18.dp))
        LabelValue("Local address", state.localAddress ?: "Waiting…")
        Spacer(Modifier.height(18.dp))
        LabelValue("AirPlay endpoint", "TCP 7000")
        Spacer(Modifier.height(18.dp))
        LabelValue("Sender", state.remoteAddress ?: "None")
    }
}

@Composable
private fun CardShell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(Color(0xFF111720), RoundedCornerShape(22.dp))
            .padding(30.dp)
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Text(label, color = Color(0xFF6F7D89), fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(value, color = Color(0xFFDDE7E4), fontSize = 16.sp)
}

private fun statusEyebrow(phase: ReceiverPhase) = when (phase) {
    ReceiverPhase.ADVERTISING -> "READY"
    ReceiverPhase.CLIENT_CONNECTED -> "SENDER CONNECTED"
    ReceiverPhase.PERMISSION_REQUIRED -> "ACTION REQUIRED"
    ReceiverPhase.ERROR -> "RECEIVER ERROR"
    ReceiverPhase.STARTING -> "STARTING"
    ReceiverPhase.STOPPED -> "OFFLINE"
}

private fun statusTitle(phase: ReceiverPhase) = when (phase) {
    ReceiverPhase.ADVERTISING -> "Ready to cast"
    ReceiverPhase.CLIENT_CONNECTED -> "AirPlay handshake detected"
    ReceiverPhase.PERMISSION_REQUIRED -> "Local network permission needed"
    ReceiverPhase.ERROR -> "AtrisCast needs attention"
    ReceiverPhase.STARTING -> "Starting receiver…"
    ReceiverPhase.STOPPED -> "Receiver stopped"
}

private fun statusBody(state: ReceiverState) = when (state.phase) {
    ReceiverPhase.ADVERTISING -> "Open Screen Mirroring on your iPhone, iPad or Mac. AtrisCast is advertising itself on the local network."
    ReceiverPhase.CLIENT_CONNECTED -> "A sender reached AtrisCast. This first milestone records the RTSP handshake; pairing and media playback are the next protocol layer."
    ReceiverPhase.PERMISSION_REQUIRED -> "Android 17 requires explicit permission before an app can advertise or accept connections on your LAN."
    ReceiverPhase.ERROR -> "The local receiver could not start cleanly. Restart it after checking the network connection."
    ReceiverPhase.STARTING -> "Opening the local RTSP endpoint and registering AirPlay discovery services."
    ReceiverPhase.STOPPED -> "Start AtrisCast to make this TV discoverable on your local network."
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

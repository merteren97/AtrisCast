package com.atrishub.atriscast

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.atrishub.atriscast.receiver.ReceiverPreferences
import com.atrishub.atriscast.receiver.ReceiverUiVisibility
import com.atrishub.atriscast.ui.AtrisCastApp
import com.atrishub.atriscast.ui.AtrisCastTheme

class MainActivity : ComponentActivity() {
    private var backgroundMirrorPermissionPromptShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtrisCastTheme {
                AtrisCastApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ReceiverUiVisibility.setVisible(true)
    }

    override fun onResume() {
        super.onResume()
        offerBackgroundMirrorPermission()
    }

    override fun onStop() {
        ReceiverUiVisibility.setVisible(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun offerBackgroundMirrorPermission() {
        if (backgroundMirrorPermissionPromptShown || Settings.canDrawOverlays(this)) return
        backgroundMirrorPermissionPromptShown = true

        val turkish = ReceiverPreferences(this).languageCode == ReceiverPreferences.LANGUAGE_TURKISH
        AlertDialog.Builder(this)
            .setTitle(if (turkish) "Arka planda ekran yansıtma" else "Background screen mirroring")
            .setMessage(
                if (turkish) {
                    "AtrisCast servisi TV'de arka planda çalışırken iPhone'dan yansıtma başladığında görüntünün otomatik açılabilmesi için Android'in ‘diğer uygulamaların üzerinde göster’ izni gerekir. Bu izin yalnızca aktif AirPlay oturumu sırasında tam ekran video yüzeyi göstermek için kullanılır."
                } else {
                    "Android's ‘display over other apps’ permission lets AtrisCast show the AirPlay picture automatically when its receiver service is running but the app is not open. AtrisCast uses it only for the full-screen video surface during an active AirPlay session."
                }
            )
            .setPositiveButton(if (turkish) "İzni aç" else "Enable") { _, _ ->
                openOverlayPermissionSettings()
            }
            .setNegativeButton(if (turkish) "Şimdi değil" else "Not now", null)
            .show()
    }

    private fun openOverlayPermissionSettings() {
        val appSpecific = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(appSpecific) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
    }

    companion object {
        const val ACTION_SHOW_MIRROR = "com.atrishub.atriscast.action.SHOW_MIRROR"
    }
}

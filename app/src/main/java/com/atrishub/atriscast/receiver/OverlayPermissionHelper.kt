package com.atrishub.atriscast.receiver

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Checks and launches background-to-foreground display capabilities on Android TV. */
object OverlayPermissionHelper {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }

    fun canOpenOverlaySettings(context: Context): Boolean =
        resolveOverlaySettingsIntent(context) != null

    fun openOverlaySettings(context: Context): Boolean {
        val intent = resolveOverlaySettingsIntent(context) ?: return false
        return runCatching {
            context.startActivity(intent)
        }.isSuccess
    }

    private fun resolveOverlaySettingsIntent(context: Context): Intent? {
        val pm = context.packageManager
        val appSpecific = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pm.resolveActivity(appSpecific, 0) != null) return appSpecific

        val generic = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pm.resolveActivity(generic, 0) != null) return generic

        return null
    }
}

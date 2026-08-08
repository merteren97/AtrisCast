package com.atrishub.atriscast.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = ReceiverPreferences(context)
        if (!preferences.startOnBoot || !LocalNetworkPermission.isGranted(context)) return
        runCatching { AtrisCastReceiverService.start(context) }
    }
}

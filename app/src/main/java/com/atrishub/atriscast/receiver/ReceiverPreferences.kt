package com.atrishub.atriscast.receiver

import android.content.Context

class ReceiverPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, DEFAULT_NAME).orEmpty().ifBlank { DEFAULT_NAME }
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value.trim().ifBlank { DEFAULT_NAME }).apply()

    var startOnBoot: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    companion object {
        private const val PREFS = "atriscast_receiver"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        const val DEFAULT_NAME = "AtrisCast"
    }
}

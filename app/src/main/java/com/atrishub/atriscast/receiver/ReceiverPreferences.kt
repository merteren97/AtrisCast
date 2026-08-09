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

    var languageCode: String
        get() = prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH).orEmpty().ifBlank { LANGUAGE_ENGLISH }
        set(value) = prefs.edit().putString(KEY_LANGUAGE, normalizeLanguage(value)).apply()

    private fun normalizeLanguage(value: String) = when (value.lowercase()) {
        LANGUAGE_TURKISH -> LANGUAGE_TURKISH
        else -> LANGUAGE_ENGLISH
    }

    companion object {
        private const val PREFS = "atriscast_receiver"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_LANGUAGE = "language"

        const val DEFAULT_NAME = "AtrisCast"
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_TURKISH = "tr"
    }
}

package com.atrishub.atriscast.receiver

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

class DeviceIdentity(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val persistentId: String by lazy {
        prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_ID, it).apply()
        }
    }

    val deviceId: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest(persistentId.toByteArray())
        val bytes = digest.take(6).toMutableList()
        // Locally administered, unicast MAC-style identifier. This is not the hardware MAC address.
        bytes[0] = ((bytes[0].toInt() or 0x02) and 0xFE).toByte()
        bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
    }

    val raopPrefix: String get() = deviceId.replace(":", "")

    companion object {
        private const val PREFS = "atriscast_identity"
        private const val KEY_ID = "persistent_id"
    }
}

package com.atrishub.atriscast.airplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.atrishub.atriscast.receiver.DeviceIdentity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Publishes the Bonjour/DNS-SD records AirPlay senders use for discovery.
 *
 * This module deliberately owns discovery only. Pairing, FairPlay session handling,
 * RTP, decode and playback are separate future protocol layers.
 */
class MdnsAdvertiser(
    context: Context,
    private val identity: DeviceIdentity,
    private val onReady: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val registered = AtomicInteger(0)
    private var started = false
    private var airPlayListener: NsdManager.RegistrationListener? = null
    private var raopListener: NsdManager.RegistrationListener? = null

    @Synchronized
    fun start(displayName: String) {
        if (started) return
        started = true
        registered.set(0)

        registerAirPlay(displayName)
        registerRaop(displayName)
    }

    @Synchronized
    fun stop() {
        airPlayListener?.let { runCatching { nsd.unregisterService(it) } }
        raopListener?.let { runCatching { nsd.unregisterService(it) } }
        airPlayListener = null
        raopListener = null
        started = false
        registered.set(0)
    }

    private fun registerAirPlay(displayName: String) {
        val info = NsdServiceInfo().apply {
            serviceName = displayName
            serviceType = AIRPLAY_TYPE
            port = AIRPLAY_PORT
            setAttribute("deviceid", identity.deviceId)
            setAttribute("features", DISCOVERY_FEATURES)
            setAttribute("flags", "0x4")
            setAttribute("model", COMPAT_MODEL)
            setAttribute("srcvers", COMPAT_SOURCE_VERSION)
            setAttribute("vv", "2")
            setAttribute("pi", identity.persistentId)
        }

        airPlayListener = listener("AirPlay") { actualName ->
            if (registered.incrementAndGet() >= 2) onReady(actualName)
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, airPlayListener!!)
    }

    private fun registerRaop(displayName: String) {
        val info = NsdServiceInfo().apply {
            serviceName = "${identity.raopPrefix}@$displayName"
            serviceType = RAOP_TYPE
            port = AIRPLAY_PORT
            setAttribute("cn", "0,1,2,3")
            setAttribute("da", "true")
            setAttribute("et", "0,3,5")
            setAttribute("md", "0,1,2")
            setAttribute("sv", "false")
            setAttribute("tp", "UDP")
            setAttribute("vn", "65537")
            setAttribute("vs", COMPAT_SOURCE_VERSION)
            setAttribute("am", COMPAT_MODEL)
        }

        raopListener = listener("RAOP") {
            if (registered.incrementAndGet() >= 2) onReady(displayName)
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, raopListener!!)
    }

    private fun listener(label: String, onRegistered: (String) -> Unit) =
        object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) =
                onRegistered(serviceInfo.serviceName)

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                started = false
                onError("$label mDNS registration failed ($errorCode)")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }

    companion object {
        const val AIRPLAY_PORT = 7000
        private const val AIRPLAY_TYPE = "_airplay._tcp"
        private const val RAOP_TYPE = "_raop._tcp"

        // Compatibility advertisement for the discovery milestone. These values will become
        // capability-driven as pairing/mirroring/audio support lands.
        private const val DISCOVERY_FEATURES = "0x5A7FFFF7,0x1E"
        private const val COMPAT_MODEL = "AppleTV5,3"
        private const val COMPAT_SOURCE_VERSION = "220.68"
    }
}

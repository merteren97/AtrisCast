package com.atrishub.atriscast.airplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.atrishub.atriscast.receiver.DeviceIdentity
import java.util.concurrent.atomic.AtomicInteger

/** Publishes the Bonjour/DNS-SD records AirPlay senders use for discovery. */
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
            serviceType = AirPlayProfile.AIRPLAY_TYPE
            port = AirPlayProfile.AIRPLAY_PORT
            setAttribute("deviceid", identity.deviceId)
            setAttribute("features", AirPlayProfile.DISCOVERY_FEATURES)
            setAttribute("flags", AirPlayProfile.FLAGS)
            setAttribute("model", AirPlayProfile.MODEL)
            setAttribute("srcvers", AirPlayProfile.SOURCE_VERSION)
            setAttribute("vv", AirPlayProfile.PROTOCOL_VERSION.toString())
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
            serviceType = AirPlayProfile.RAOP_TYPE
            port = AirPlayProfile.AIRPLAY_PORT
            setAttribute("cn", "0,1,2,3")
            setAttribute("da", "true")
            setAttribute("et", "0,3,5")
            setAttribute("md", "0,1,2")
            setAttribute("sv", "false")
            setAttribute("tp", "UDP")
            setAttribute("vn", "65537")
            setAttribute("vs", AirPlayProfile.SOURCE_VERSION)
            setAttribute("am", AirPlayProfile.MODEL)
            setAttribute("sf", AirPlayProfile.FLAGS)
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
}

package com.atrishub.atriscast.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.atrishub.atriscast.MainActivity
import com.atrishub.atriscast.R
import com.atrishub.atriscast.airplay.AirPlaySocketServer
import com.atrishub.atriscast.airplay.MdnsAdvertiser

class AtrisCastReceiverService : Service() {
    private lateinit var preferences: ReceiverPreferences
    private lateinit var identity: DeviceIdentity
    private lateinit var networkInfo: NetworkInfoProvider
    private var multicastLock: WifiManager.MulticastLock? = null
    private var advertiser: MdnsAdvertiser? = null
    private var socketServer: AirPlaySocketServer? = null

    override fun onCreate() {
        super.onCreate()
        preferences = ReceiverPreferences(this)
        identity = DeviceIdentity(this)
        networkInfo = NetworkInfoProvider(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!LocalNetworkPermission.isGranted(this)) {
            ReceiverRuntime.update {
                it.copy(
                    phase = ReceiverPhase.PERMISSION_REQUIRED,
                    networkLabel = networkInfo.networkLabel(),
                    localAddress = networkInfo.localIpv4(),
                    error = null,
                )
            }
            stopSelf()
            return START_NOT_STICKY
        }

        startReceiver()
        return START_STICKY
    }

    override fun onDestroy() {
        stopReceiver()
        ReceiverRuntime.update { it.copy(phase = ReceiverPhase.STOPPED, remoteAddress = null) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startReceiver() {
        if (advertiser != null || socketServer != null) return

        ReceiverRuntime.replace(
            ReceiverState(
                phase = ReceiverPhase.STARTING,
                advertisedName = preferences.displayName,
                networkLabel = networkInfo.networkLabel(),
                localAddress = networkInfo.localIpv4(),
            )
        )

        acquireMulticastLock()

        val server = AirPlaySocketServer(
            onClient = { remote ->
                ReceiverRuntime.update { it.copy(phase = ReceiverPhase.CLIENT_CONNECTED, remoteAddress = remote) }
            },
            onRequest = { request -> ReceiverRuntime.update { it.copy(lastRequest = request) } },
            onClientClosed = {
                ReceiverRuntime.update { it.copy(phase = ReceiverPhase.ADVERTISING, remoteAddress = null) }
            },
            onError = { message -> ReceiverRuntime.update { it.copy(phase = ReceiverPhase.ERROR, error = message) } },
        )
        val serverStart = server.start()
        if (serverStart.isFailure) {
            val message = serverStart.exceptionOrNull()?.message ?: "Could not bind TCP 7000"
            ReceiverRuntime.update { it.copy(phase = ReceiverPhase.ERROR, error = "RTSP server failed: $message") }
            server.stop()
            multicastLock?.let { if (it.isHeld) it.release() }
            multicastLock = null
            return
        }
        socketServer = server

        advertiser = MdnsAdvertiser(
            context = this,
            identity = identity,
            onReady = { actualName ->
                ReceiverRuntime.update {
                    it.copy(
                        phase = ReceiverPhase.ADVERTISING,
                        advertisedName = actualName,
                        networkLabel = networkInfo.networkLabel(),
                        localAddress = networkInfo.localIpv4(),
                        error = null,
                    )
                }
            },
            onError = { message -> ReceiverRuntime.update { it.copy(phase = ReceiverPhase.ERROR, error = message) } },
        ).also { it.start(preferences.displayName) }
    }

    private fun stopReceiver() {
        advertiser?.stop()
        advertiser = null
        socketServer?.stop()
        socketServer = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    private fun acquireMulticastLock() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("AtrisCast:mDNS").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_atriscast)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text))
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()

    companion object {
        private const val CHANNEL_ID = "atriscast_receiver"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AtrisCastReceiverService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AtrisCastReceiverService::class.java))
        }
    }
}

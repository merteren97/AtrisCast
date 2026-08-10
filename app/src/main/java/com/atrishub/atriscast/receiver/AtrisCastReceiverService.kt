package com.atrishub.atriscast.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.atrishub.atriscast.MainActivity
import com.atrishub.atriscast.R
import com.atrishub.atriscast.airplay.AirPlayProfile
import com.atrishub.atriscast.airplay.AirPlaySocketServer
import com.atrishub.atriscast.airplay.MdnsAdvertiser

class AtrisCastReceiverService : Service() {
    private lateinit var preferences: ReceiverPreferences
    private lateinit var identity: DeviceIdentity
    private lateinit var networkInfo: NetworkInfoProvider
    private lateinit var mirrorOverlay: MirrorOverlayController

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiPerformanceLock: WifiManager.WifiLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var advertiser: MdnsAdvertiser? = null
    private var socketServer: AirPlaySocketServer? = null
    private var uiVisibilityListener: ((Boolean) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        preferences = ReceiverPreferences(this)
        identity = DeviceIdentity(this)
        networkInfo = NetworkInfoProvider(this)
        mirrorOverlay = MirrorOverlayController(applicationContext)

        uiVisibilityListener = { visible ->
            // If the user leaves AtrisCast while mirroring, create the service-owned overlay. Once
            // created, keep that Surface stable for the rest of the session even if MainActivity is
            // later brought forward; switching MediaCodec targets mid-stream causes a visible hitch.
            if (!visible && ReceiverRuntime.state.value.mirrorActive) {
                showMirrorSurfaceIfNeeded()
            }
        }
        ReceiverUiVisibility.setListener(uiVisibilityListener)

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
        ReceiverUiVisibility.setListener(null)
        uiVisibilityListener = null
        stopReceiver()
        ReceiverRuntime.update {
            it.copy(
                phase = ReceiverPhase.STOPPED,
                remoteAddress = null,
                mirrorActive = false,
                videoFramesRendered = 0L,
                videoResolution = null,
                videoWidth = null,
                videoHeight = null,
                videoError = null,
                audioActive = false,
                audioError = null,
            )
        }
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
            displayName = preferences.displayName,
            deviceId = identity.deviceId,
            persistentId = identity.persistentId,
            surfaceProvider = MirrorSurfaceRegistry::current,
            onClient = { remote ->
                stopMirrorPresentation()
                ReceiverRuntime.update {
                    it.copy(
                        phase = ReceiverPhase.CLIENT_CONNECTED,
                        remoteAddress = remote,
                        lastSenderAddress = remote,
                        lastRequest = null,
                        protocolStage = ProtocolStage.DISCOVERY,
                        mediaBytesReceived = 0L,
                        mirrorActive = false,
                        videoFramesRendered = 0L,
                        videoResolution = null,
                        videoWidth = null,
                        videoHeight = null,
                        videoError = null,
                        audioActive = false,
                        audioError = null,
                        error = null,
                    )
                }
            },
            onRequest = { request ->
                ReceiverRuntime.update {
                    it.copy(
                        lastRequest = request,
                        protocolStage = requestStage(request, it.protocolStage),
                    )
                }
            },
            onTransportReady = { summary ->
                ReceiverRuntime.update {
                    it.copy(
                        lastRequest = summary,
                        protocolStage = maxStage(it.protocolStage, ProtocolStage.TRANSPORT),
                        error = null,
                    )
                }
            },
            onMirrorStarted = {
                ReceiverRuntime.update {
                    it.copy(
                        mirrorActive = true,
                        protocolStage = ProtocolStage.STREAMING,
                        videoFramesRendered = 0L,
                        videoResolution = null,
                        videoWidth = null,
                        videoHeight = null,
                        videoError = null,
                        lastRequest = "Mirror stream connected",
                        error = null,
                    )
                }
                acquireStreamingPerformanceLocks()
                showMirrorSurfaceIfNeeded()
            },
            onMediaActivity = { bytes ->
                ReceiverRuntime.update {
                    val total = it.mediaBytesReceived + bytes
                    it.copy(
                        protocolStage = ProtocolStage.STREAMING,
                        mediaBytesReceived = total,
                        lastRequest = "RECORD • AirPlay media • $total B received",
                        error = null,
                    )
                }
            },
            onVideoFrameRendered = {
                ReceiverRuntime.update {
                    it.copy(
                        mirrorActive = true,
                        videoFramesRendered = it.videoFramesRendered + 1,
                        videoError = null,
                    )
                }
            },
            onVideoFormat = { format ->
                ReceiverRuntime.update { it.copy(videoResolution = format, videoError = null) }
            },
            onVideoGeometry = { width, height ->
                mirrorOverlay.updateGeometry(width, height)
                ReceiverRuntime.update {
                    it.copy(
                        videoWidth = width.takeIf { value -> value > 0 },
                        videoHeight = height.takeIf { value -> value > 0 },
                    )
                }
            },
            onMirrorError = { message ->
                ReceiverRuntime.update {
                    it.copy(
                        mirrorActive = true,
                        videoError = message,
                        lastRequest = message,
                    )
                }
            },
            onMirrorStopped = {
                stopMirrorPresentation()
                ReceiverRuntime.update {
                    it.copy(
                        mirrorActive = false,
                        videoFramesRendered = 0L,
                        videoResolution = null,
                        videoWidth = null,
                        videoHeight = null,
                    )
                }
            },
            onAudioStarted = {
                ReceiverRuntime.update {
                    it.copy(
                        audioActive = true,
                        audioError = null,
                    )
                }
            },
            onAudioError = { message ->
                ReceiverRuntime.update {
                    it.copy(
                        audioActive = false,
                        audioError = message,
                        lastRequest = message,
                    )
                }
            },
            onClientClosed = {
                stopMirrorPresentation()
                ReceiverRuntime.update {
                    it.copy(
                        phase = ReceiverPhase.ADVERTISING,
                        remoteAddress = null,
                        mirrorActive = false,
                        videoFramesRendered = 0L,
                        videoResolution = null,
                        videoWidth = null,
                        videoHeight = null,
                        videoError = null,
                        audioActive = false,
                        audioError = null,
                    )
                }
            },
            onError = { message -> ReceiverRuntime.update { it.copy(phase = ReceiverPhase.ERROR, error = message) } },
        )
        val serverStart = server.start()
        if (serverStart.isFailure) {
            val message = serverStart.exceptionOrNull()?.message ?: "Could not bind TCP ${AirPlayProfile.AIRPLAY_PORT}"
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

    private fun showMirrorSurfaceIfNeeded() {
        if (ReceiverUiVisibility.isVisible()) {
            mirrorOverlay.hide()
            return
        }

        // Android 10+ intentionally restricts background Activity launches. When the user granted
        // the special overlay permission, render directly from the foreground receiver service;
        // otherwise keep the old best-effort Activity start as a compatibility fallback.
        if (!mirrorOverlay.show()) bringMirrorUiToForeground()
    }

    private fun bringMirrorUiToForeground() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SHOW_MIRROR
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireStreamingPerformanceLocks() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiLock = wifiPerformanceLock ?: wifi.createWifiLock(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            },
            "AtrisCast:AirPlayLowLatency",
        ).apply {
            setReferenceCounted(false)
            wifiPerformanceLock = this
        }
        if (!wifiLock.isHeld) runCatching { wifiLock.acquire() }

        val power = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = cpuWakeLock ?: power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AtrisCast:AirPlayMirror",
        ).apply {
            setReferenceCounted(false)
            cpuWakeLock = this
        }
        if (!wakeLock.isHeld) {
            runCatching { wakeLock.acquire(STREAM_WAKE_LOCK_TIMEOUT_MS) }
        }
    }

    private fun releaseStreamingPerformanceLocks() {
        wifiPerformanceLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wifiPerformanceLock = null
        cpuWakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        cpuWakeLock = null
    }

    private fun stopMirrorPresentation() {
        mirrorOverlay.hide()
        releaseStreamingPerformanceLocks()
    }

    private fun requestStage(request: String, current: ProtocolStage): ProtocolStage {
        val normalized = request.uppercase()
        val next = when {
            normalized.contains("/FP-SETUP") -> ProtocolStage.FAIRPLAY
            normalized.contains("/INFO") || normalized.startsWith("OPTIONS") -> ProtocolStage.NEGOTIATION
            else -> current
        }
        return maxStage(current, next)
    }

    private fun maxStage(current: ProtocolStage, candidate: ProtocolStage): ProtocolStage =
        if (candidate.ordinal > current.ordinal) candidate else current

    private fun stopReceiver() {
        stopMirrorPresentation()
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

    private fun buildNotification(): android.app.Notification {
        val turkish = preferences.languageCode == ReceiverPreferences.LANGUAGE_TURKISH
        val title = if (turkish) "AtrisCast alıcısı etkin" else "AtrisCast receiver active"
        val text = if (turkish) "Bu TV yerel ağda yayın için hazır." else "This TV is ready for local casting."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_atriscast)
            .setContentTitle(title)
            .setContentText(text)
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
    }

    companion object {
        private const val CHANNEL_ID = "atriscast_receiver"
        private const val NOTIFICATION_ID = 1001
        private const val STREAM_WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AtrisCastReceiverService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AtrisCastReceiverService::class.java))
        }

        fun restart(context: Context) {
            stop(context)
            start(context)
        }
    }
}

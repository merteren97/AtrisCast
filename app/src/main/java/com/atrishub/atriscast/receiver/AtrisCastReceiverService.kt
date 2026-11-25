package com.atrishub.atriscast.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.atrishub.atriscast.MainActivity
import com.atrishub.atriscast.R
import com.atrishub.atriscast.airplay.AirPlayProfile
import com.atrishub.atriscast.airplay.AirPlaySocketServer
import com.atrishub.atriscast.airplay.MdnsAdvertiser
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AtrisCastReceiverService : Service() {
    private lateinit var preferences: ReceiverPreferences
    private lateinit var identity: DeviceIdentity
    private lateinit var networkInfo: NetworkInfoProvider
    private lateinit var mirrorOverlay: MirrorOverlayController
    private val mainHandler = Handler(Looper.getMainLooper())

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLowLatencyLock: WifiManager.WifiLock? = null
    private var wifiHighPerformanceLock: WifiManager.WifiLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var advertiser: MdnsAdvertiser? = null
    private var socketServer: AirPlaySocketServer? = null
    private var uiVisibilityListener: ((Boolean) -> Unit)? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val streamTelemetryEnabled = AtomicBoolean(false)
    private val mediaBytesCounter = AtomicLong(0L)
    private val renderedFramesCounter = AtomicLong(0L)
    private val lastTelemetryPublishNanos = AtomicLong(0L)
    private var lastObservedIpv4: String? = null

    private val overlayFallbackRunnable = Runnable {
        if (ReceiverRuntime.state.value.mirrorActive && !ReceiverUiVisibility.isVisible()) {
            mirrorOverlay.show()
        }
    }

    private val hideOverlayAfterActivityRunnable = Runnable {
        if (ReceiverUiVisibility.isVisible()) {
            mirrorOverlay.hide()
        }
    }

    private val networkRefreshRunnable = Runnable {
        refreshNetworkAndMdns()
    }

    override fun onCreate() {
        super.onCreate()
        preferences = ReceiverPreferences(this)
        identity = DeviceIdentity(this)
        networkInfo = NetworkInfoProvider(this)
        mirrorOverlay = MirrorOverlayController(applicationContext) {
            bringMirrorUiToForeground()
        }

        uiVisibilityListener = { visible ->
            if (visible) {
                mainHandler.removeCallbacks(overlayFallbackRunnable)
                cancelCastAlertNotification()
                // Allow MainActivity's SurfaceView a brief moment to mount before detaching overlay.
                mainHandler.postDelayed(hideOverlayAfterActivityRunnable, OVERLAY_HANDOFF_DELAY_MS)
            } else if (ReceiverRuntime.state.value.mirrorActive && preferences.autoOpenOnCast) {
                mainHandler.removeCallbacks(hideOverlayAfterActivityRunnable)
                mirrorOverlay.show()
            }
        }
        ReceiverUiVisibility.setListener(uiVisibilityListener)

        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT_MIRROR -> {
                socketServer?.stopActiveSession()
                return START_STICKY
            }
            ACTION_REFRESH_NOTIFICATION -> {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification())
                return START_STICKY
            }
        }

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
        mainHandler.removeCallbacksAndMessages(null)
        ReceiverUiVisibility.setListener(null)
        uiVisibilityListener = null
        streamTelemetryEnabled.set(false)
        cancelCastAlertNotification()
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

        val initialIpv4 = networkInfo.localIpv4()
        lastObservedIpv4 = initialIpv4
        ReceiverRuntime.replace(
            ReceiverState(
                phase = ReceiverPhase.STARTING,
                advertisedName = preferences.displayName,
                networkLabel = networkInfo.networkLabel(),
                localAddress = initialIpv4,
            )
        )

        acquireMulticastLock()

        val server = AirPlaySocketServer(
            displayName = preferences.displayName,
            deviceId = identity.deviceId,
            persistentId = identity.persistentId,
            surfaceProvider = MirrorSurfaceRegistry::current,
            onClient = { remote ->
                streamTelemetryEnabled.set(false)
                stopMirrorPresentation()
                resetStreamTelemetry()
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
                resetStreamTelemetry()
                streamTelemetryEnabled.set(true)
                ReceiverRuntime.update {
                    it.copy(
                        mirrorActive = true,
                        protocolStage = ProtocolStage.STREAMING,
                        mediaBytesReceived = 0L,
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
                mediaBytesCounter.addAndGet(bytes)
                publishStreamTelemetry()
            },
            onVideoFrameRendered = {
                val frames = renderedFramesCounter.incrementAndGet()
                if (frames == 1L) mirrorOverlay.markFirstFrameRendered()
                publishStreamTelemetry(force = frames == 1L)
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
                streamTelemetryEnabled.set(false)
                resetStreamTelemetry()
                stopMirrorPresentation()
                ReceiverRuntime.update {
                    it.copy(
                        mirrorActive = false,
                        mediaBytesReceived = 0L,
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
                streamTelemetryEnabled.set(false)
                resetStreamTelemetry()
                stopMirrorPresentation()
                ReceiverRuntime.update {
                    it.copy(
                        phase = ReceiverPhase.ADVERTISING,
                        remoteAddress = null,
                        mirrorActive = false,
                        mediaBytesReceived = 0L,
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

        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = scheduleNetworkRefresh()
            override fun onLost(network: Network) = scheduleNetworkRefresh()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                scheduleNetworkRefresh()
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    private fun scheduleNetworkRefresh() {
        mainHandler.removeCallbacks(networkRefreshRunnable)
        mainHandler.postDelayed(networkRefreshRunnable, NETWORK_REFRESH_DEBOUNCE_MS)
    }

    private fun refreshNetworkAndMdns() {
        val newLabel = networkInfo.networkLabel()
        val newIpv4 = networkInfo.localIpv4()
        val ipChanged = newIpv4 != lastObservedIpv4
        lastObservedIpv4 = newIpv4

        ReceiverRuntime.update {
            it.copy(
                networkLabel = newLabel,
                localAddress = newIpv4,
            )
        }

        if (ipChanged && newIpv4 != null && !ReceiverRuntime.state.value.mirrorActive) {
            advertiser?.restart(preferences.displayName)
        }
    }

    private fun resetStreamTelemetry() {
        mediaBytesCounter.set(0L)
        renderedFramesCounter.set(0L)
        lastTelemetryPublishNanos.set(0L)
    }

    private fun publishStreamTelemetry(force: Boolean = false) {
        if (!streamTelemetryEnabled.get()) return

        val now = System.nanoTime()
        if (force) {
            lastTelemetryPublishNanos.set(now)
        } else {
            val previous = lastTelemetryPublishNanos.get()
            if (previous != 0L && now - previous < STREAM_TELEMETRY_INTERVAL_NS) return
            if (!lastTelemetryPublishNanos.compareAndSet(previous, now)) return
        }

        val totalBytes = mediaBytesCounter.get()
        val frames = renderedFramesCounter.get()
        ReceiverRuntime.update {
            it.copy(
                protocolStage = ProtocolStage.STREAMING,
                mediaBytesReceived = totalBytes,
                mirrorActive = it.mirrorActive || frames > 0L,
                videoFramesRendered = frames,
                videoError = if (frames > 0L) null else it.videoError,
                lastRequest = "RECORD • AirPlay media • $totalBytes B received",
                error = null,
            )
        }
    }

    private fun showMirrorSurfaceIfNeeded() {
        if (ReceiverUiVisibility.isVisible()) {
            mirrorOverlay.hide()
            return
        }
        if (!preferences.autoOpenOnCast) return

        wakeDisplayBriefly()

        // 1. Always attempt to launch MainActivity fullscreen first. When SYSTEM_ALERT_WINDOW is
        //    granted, Android exempts this background Activity start so the user lands directly on
        //    MirrorPlaybackScreen with full D-pad/OSD/Back support and zero surface switch hitch.
        bringMirrorUiToForeground()

        // 2. If overlay permission is missing, post a high-priority Full-Screen Intent casting
        //    notification as an OS-sanctioned fallback on Android TV builds that support FSI.
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            postCastFullScreenNotification()
        }

        // 3. If MainActivity still has not reached the foreground within 650 ms (e.g., blocked by
        //    the current screen or OS policy), fall back to the translucent service overlay.
        mainHandler.removeCallbacks(overlayFallbackRunnable)
        mainHandler.postDelayed(overlayFallbackRunnable, OVERLAY_FALLBACK_DELAY_MS)
    }

    private fun bringMirrorUiToForeground() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SHOW_MIRROR
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeDisplayBriefly() {
        val power = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        runCatching {
            val wake = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AtrisCast:WakeDisplay",
            )
            wake.acquire(DISPLAY_WAKE_MS)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireStreamingPerformanceLocks() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val highPerformance = wifiHighPerformanceLock ?: wifi.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "AtrisCast:AirPlayHighPerf",
        ).apply {
            setReferenceCounted(false)
            wifiHighPerformanceLock = this
        }
        if (!highPerformance.isHeld) runCatching { highPerformance.acquire() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val lowLatency = wifiLowLatencyLock ?: wifi.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "AtrisCast:AirPlayLowLatency",
            ).apply {
                setReferenceCounted(false)
                wifiLowLatencyLock = this
            }
            if (!lowLatency.isHeld) runCatching { lowLatency.acquire() }
        }

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
        wifiLowLatencyLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wifiLowLatencyLock = null
        wifiHighPerformanceLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wifiHighPerformanceLock = null
        cpuWakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        cpuWakeLock = null
    }

    private fun stopMirrorPresentation() {
        mainHandler.removeCallbacks(overlayFallbackRunnable)
        mainHandler.removeCallbacks(hideOverlayAfterActivityRunnable)
        cancelCastAlertNotification()
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
        unregisterNetworkCallback()
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

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val castChannel = NotificationChannel(
            CAST_CHANNEL_ID,
            getString(R.string.cast_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.cast_notification_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(castChannel)
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

    private fun postCastFullScreenNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val turkish = preferences.languageCode == ReceiverPreferences.LANGUAGE_TURKISH
        val showIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHOW_MIRROR
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CAST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_atriscast)
            .setContentTitle(if (turkish) "AirPlay ekran yansıtma aktif" else "AirPlay mirroring active")
            .setContentText(
                if (turkish) "Tam ekran açmak için seçin." else "Select to open fullscreen mirroring."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(showIntent)
            .setFullScreenIntent(showIntent, true)
            .build()
        runCatching { manager.notify(CAST_NOTIFICATION_ID, notification) }
    }

    private fun cancelCastAlertNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(CAST_NOTIFICATION_ID)
        }
    }

    companion object {
        private const val CHANNEL_ID = "atriscast_receiver"
        private const val CAST_CHANNEL_ID = "atriscast_cast_alert"
        private const val NOTIFICATION_ID = 1001
        private const val CAST_NOTIFICATION_ID = 1002
        private const val STREAM_WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1_000L
        private const val STREAM_TELEMETRY_INTERVAL_NS = 250_000_000L
        private const val OVERLAY_FALLBACK_DELAY_MS = 650L
        private const val OVERLAY_HANDOFF_DELAY_MS = 180L
        private const val NETWORK_REFRESH_DEBOUNCE_MS = 1_000L
        private const val DISPLAY_WAKE_MS = 3_000L

        const val ACTION_DISCONNECT_MIRROR = "com.atrishub.atriscast.action.DISCONNECT_MIRROR"
        const val ACTION_REFRESH_NOTIFICATION = "com.atrishub.atriscast.action.REFRESH_NOTIFICATION"

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

        fun disconnectMirror(context: Context) {
            context.startService(
                Intent(context, AtrisCastReceiverService::class.java).apply {
                    action = ACTION_DISCONNECT_MIRROR
                }
            )
        }

        fun refreshNotification(context: Context) {
            context.startService(
                Intent(context, AtrisCastReceiverService::class.java).apply {
                    action = ACTION_REFRESH_NOTIFICATION
                }
            )
        }
    }
}

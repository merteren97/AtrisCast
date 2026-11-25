package com.atrishub.atriscast.receiver

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Owns a full-screen SurfaceView overlay while AtrisCast's Activity is not visible.
 *
 * Uses PixelFormat.TRANSLUCENT on the host window so SurfaceView hole-punching (`setZOrderOnTop(false)`)
 * composites cleanly on all Android TV hardware compositors.
 */
class MirrorOverlayController(
    context: Context,
    private val onShowFailed: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var requestedVisible = false
    @Volatile private var visibleWidth = 0
    @Volatile private var visibleHeight = 0
    @Volatile private var framesRendered = false

    private var rootView: FrameLayout? = null
    private var surfaceView: SurfaceView? = null
    private var loadingLabel: TextView? = null
    private var attachedSurface: Surface? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(appContext)

    /** Returns false when Android's "display over other apps" permission is not granted. */
    fun show(): Boolean {
        if (!canShow()) return false
        requestedVisible = true
        mainHandler.post {
            if (requestedVisible && canShow()) ensureOverlay()
        }
        return true
    }

    fun hide() {
        requestedVisible = false
        framesRendered = false
        mainHandler.post(::removeOverlay)
    }

    fun markFirstFrameRendered() {
        if (framesRendered) return
        framesRendered = true
        mainHandler.post { loadingLabel?.visibility = View.GONE }
    }

    fun updateGeometry(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        visibleWidth = width
        visibleHeight = height
        mainHandler.post(::applySurfaceGeometry)
    }

    private fun ensureOverlay() {
        if (rootView != null) {
            applySurfaceGeometry()
            return
        }

        val root = FrameLayout(appContext).apply {
            setBackgroundColor(Color.BLACK)
        }
        val mirrorSurface = SurfaceView(appContext).apply {
            keepScreenOn = true
            setZOrderOnTop(false)
            holder.setFormat(PixelFormat.OPAQUE)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    attachSurface(holder.surface)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    attachSurface(holder.surface)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    attachedSurface?.let(MirrorSurfaceRegistry::detachOverlay)
                    attachedSurface = null
                }
            })
        }
        val turkish = ReceiverPreferences(appContext).languageCode == ReceiverPreferences.LANGUAGE_TURKISH
        val statusText = TextView(appContext).apply {
            text = if (turkish) "AirPlay ekranı başlatılıyor…" else "Starting AirPlay screen…"
            setTextColor(Color.parseColor("#E6F2F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(36, 20, 36, 20)
            setBackgroundColor(Color.parseColor("#CC101922"))
            visibility = if (framesRendered) View.GONE else View.VISIBLE
        }

        root.addView(
            mirrorSurface,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
        )
        root.addView(
            statusText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            title = "AtrisCast AirPlay mirror"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val added = runCatching { windowManager.addView(root, params) }.isSuccess
        if (!added) {
            attachedSurface?.let(MirrorSurfaceRegistry::detachOverlay)
            attachedSurface = null
            requestedVisible = false
            onShowFailed()
            return
        }

        rootView = root
        surfaceView = mirrorSurface
        loadingLabel = statusText
        applySurfaceGeometry()
    }

    private fun attachSurface(surface: Surface) {
        attachedSurface?.takeIf { it !== surface }?.let(MirrorSurfaceRegistry::detachOverlay)
        attachedSurface = surface
        MirrorSurfaceRegistry.attachOverlay(surface)
    }

    private fun applySurfaceGeometry() {
        val view = surfaceView ?: return
        val width = visibleWidth
        val height = visibleHeight
        val layout = view.layoutParams as? FrameLayout.LayoutParams ?: return

        if (width <= 0 || height <= 0) {
            layout.width = FrameLayout.LayoutParams.MATCH_PARENT
            layout.height = FrameLayout.LayoutParams.MATCH_PARENT
            layout.gravity = Gravity.CENTER
            view.layoutParams = layout
            return
        }

        val (containerWidth, containerHeight) = displaySize()
        if (containerWidth <= 0 || containerHeight <= 0) return

        val videoRatio = width.toFloat() / height.toFloat()
        val containerRatio = containerWidth.toFloat() / containerHeight.toFloat()
        if (videoRatio >= containerRatio) {
            layout.width = containerWidth
            layout.height = (containerWidth / videoRatio).roundToInt().coerceAtLeast(1)
        } else {
            layout.height = containerHeight
            layout.width = (containerHeight * videoRatio).roundToInt().coerceAtLeast(1)
        }
        layout.gravity = Gravity.CENTER
        view.layoutParams = layout
    }

    private fun displaySize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = appContext.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun removeOverlay() {
        attachedSurface?.let(MirrorSurfaceRegistry::detachOverlay)
        attachedSurface = null
        rootView?.let { root -> runCatching { windowManager.removeViewImmediate(root) } }
        rootView = null
        surfaceView = null
        loadingLabel = null
    }
}

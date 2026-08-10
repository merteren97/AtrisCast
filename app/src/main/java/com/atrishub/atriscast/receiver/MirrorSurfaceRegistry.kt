package com.atrishub.atriscast.receiver

import android.view.Surface

/** Process-local handoff between visible mirror surfaces and the receiver's decoder worker. */
object MirrorSurfaceRegistry {
    @Volatile private var activitySurface: Surface? = null
    @Volatile private var overlaySurface: Surface? = null

    fun attachActivity(surface: Surface) {
        activitySurface = surface
    }

    fun detachActivity(surface: Surface) {
        if (activitySurface === surface) activitySurface = null
    }

    fun attachOverlay(surface: Surface) {
        overlaySurface = surface
    }

    fun detachOverlay(surface: Surface) {
        if (overlaySurface === surface) overlaySurface = null
    }

    fun current(): Surface? {
        val activity = activitySurface?.takeIf { it.isValid }
        val overlay = overlaySurface?.takeIf { it.isValid }
        return if (ReceiverUiVisibility.isVisible()) {
            activity ?: overlay
        } else {
            overlay ?: activity
        }
    }
}

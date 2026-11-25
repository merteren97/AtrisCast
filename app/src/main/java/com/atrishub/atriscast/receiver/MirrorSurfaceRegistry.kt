package com.atrishub.atriscast.receiver

import android.view.Surface

/** Process-local handoff between visible mirror surfaces and the receiver's decoder worker. */
object MirrorSurfaceRegistry {
    @Volatile private var activitySurface: Surface? = null
    @Volatile private var overlaySurface: Surface? = null
    @Volatile var fillScreen: Boolean = false

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
        // Prefer MainActivity's dedicated playback Surface whenever visible so users get remote
        // OSD/Back controls; fall back to the service overlay Surface when backgrounded.
        return activitySurface?.takeIf { it.isValid }
            ?: overlaySurface?.takeIf { it.isValid }
    }
}

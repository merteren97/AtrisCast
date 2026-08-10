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
        // Once a background mirror overlay has been created, keep that Surface stable until the
        // mirror session ends. Switching from overlay -> Activity merely because MainActivity became
        // visible would force MediaCodec to rebuild mid-stream and can produce another visible hitch.
        return overlaySurface?.takeIf { it.isValid }
            ?: activitySurface?.takeIf { it.isValid }
    }
}

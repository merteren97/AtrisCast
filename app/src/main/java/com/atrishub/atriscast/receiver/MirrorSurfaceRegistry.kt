package com.atrishub.atriscast.receiver

import android.view.Surface

/** Process-local handoff between the Compose SurfaceView and the receiver's decoder worker. */
object MirrorSurfaceRegistry {
    @Volatile private var currentSurface: Surface? = null

    fun attach(surface: Surface) {
        currentSurface = surface
    }

    fun detach(surface: Surface) {
        if (currentSurface === surface) currentSurface = null
    }

    fun current(): Surface? = currentSurface?.takeIf { it.isValid }
}

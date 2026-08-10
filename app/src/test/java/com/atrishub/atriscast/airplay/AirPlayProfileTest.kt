package com.atrishub.atriscast.airplay

import org.junit.Assert.assertEquals
import org.junit.Test

class AirPlayProfileTest {
    @Test
    fun realtimeMirrorProfileAdvertisesSixtyFpsCeiling() {
        assertEquals(60L, AirPlayProfile.DISPLAY_MAX_FPS)
        assertEquals(1.0 / 60.0, AirPlayProfile.DISPLAY_REFRESH_RATE, 0.0)
    }
}

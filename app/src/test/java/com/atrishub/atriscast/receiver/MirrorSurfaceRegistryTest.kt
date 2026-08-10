package com.atrishub.atriscast.receiver

import org.junit.Assert.assertNull
import org.junit.Test

class MirrorSurfaceRegistryTest {
    @Test
    fun emptyRegistryHasNoRenderSurface() {
        assertNull(MirrorSurfaceRegistry.current())
    }
}

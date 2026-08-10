package com.atrishub.atriscast.airplay

import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRealtimeQueuePolicyTest {
    @Test
    fun sixtyFpsTwelveFrameQueueStaysBelowQuarterSecond() {
        val queueCapacity = 12
        val frameRate = AirPlayProfile.DISPLAY_MAX_FPS.toDouble()
        val queueBudgetMs = queueCapacity * 1_000.0 / frameRate

        assertTrue(queueBudgetMs <= 250.0)
    }
}

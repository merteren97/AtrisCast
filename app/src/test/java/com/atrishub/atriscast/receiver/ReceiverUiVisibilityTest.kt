package com.atrishub.atriscast.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiverUiVisibilityTest {
    @Test
    fun listenerReceivesVisibilityTransitions() {
        val observed = mutableListOf<Boolean>()
        ReceiverUiVisibility.setVisible(false)
        ReceiverUiVisibility.setListener { observed += it }
        ReceiverUiVisibility.setVisible(true)
        ReceiverUiVisibility.setVisible(false)
        ReceiverUiVisibility.setListener(null)

        assertEquals(listOf(false, true, false), observed)
    }
}

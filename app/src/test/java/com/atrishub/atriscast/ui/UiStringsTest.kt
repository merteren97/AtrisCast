package com.atrishub.atriscast.ui

import com.atrishub.atriscast.receiver.ProtocolStage as ReceiverStage
import com.atrishub.atriscast.receiver.ReceiverPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStringsTest {
    @Test
    fun receiverStagesMapDirectlyToUiStages() {
        assertEquals(ProtocolStage.DISCOVERY, toUiStage(ReceiverStage.DISCOVERY))
        assertEquals(ProtocolStage.NEGOTIATION, toUiStage(ReceiverStage.NEGOTIATION))
        assertEquals(ProtocolStage.FAIRPLAY, toUiStage(ReceiverStage.FAIRPLAY))
        assertEquals(ProtocolStage.TRANSPORT, toUiStage(ReceiverStage.TRANSPORT))
        assertEquals(ProtocolStage.STREAMING, toUiStage(ReceiverStage.STREAMING))
    }

    @Test
    fun networkLabelsLocalizeForTurkishAndEnglish() {
        val tr = strings(ReceiverPreferences.LANGUAGE_TURKISH)
        val en = strings(ReceiverPreferences.LANGUAGE_ENGLISH)

        assertEquals(tr.networkOffline, localizeNetworkLabel("Offline", tr))
        assertEquals(en.networkOffline, localizeNetworkLabel("Offline", en))
        assertEquals("Wi‑Fi", localizeNetworkLabel("Wi‑Fi", tr))
    }

    @Test
    fun mediaBytesFormatUsesDotDecimalConsistently() {
        assertEquals("512 B", formatMediaBytes(512L))
        assertTrue(formatMediaBytes(2048L).endsWith("KB"))
        assertTrue(formatMediaBytes(2L * 1024L * 1024L).contains("."))
    }
}

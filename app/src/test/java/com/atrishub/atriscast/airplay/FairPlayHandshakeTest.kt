package com.atrishub.atriscast.airplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FairPlayHandshakeTest {
    private fun phase1(version: Int, mode: Int): ByteArray = ByteArray(16).also {
        it[0] = 0x46
        it[1] = 0x50
        it[2] = 0x4C
        it[3] = 0x59
        it[4] = version.toByte()
        it[5] = 0x01
        it[6] = 0x02
        it[11] = 0x04
        it[14] = mode.toByte()
    }

    private fun phase2(version: Int): ByteArray = ByteArray(164).also {
        it[0] = 0x46
        it[1] = 0x50
        it[2] = 0x4C
        it[3] = 0x59
        it[4] = version.toByte()
        for (index in 144 until 164) {
            it[index] = (index - 144).toByte()
        }
    }

    @Test
    fun v3Phase1ReturnsModeSpecific142ByteResponse() {
        for (mode in 0..3) {
            val result = FairPlayHandshake().setup(phase1(0x03, mode))
            assertEquals(142, result.size)
            assertEquals(0x03, result[4].toInt() and 0xFF)
            assertEquals(mode, result[13].toInt() and 0xFF)
        }
    }

    @Test
    fun v2Phase1PatchesModeAndDiffersFromV3() {
        val v2 = FairPlayHandshake().setup(phase1(0x02, 2))
        val v3 = FairPlayHandshake().setup(phase1(0x03, 2))

        assertEquals(142, v2.size)
        assertEquals(0x02, v2[4].toInt() and 0xFF)
        assertEquals(2, v2[13].toInt() and 0xFF)

        var identical = true
        for (index in 14 until v2.size) {
            if (v2[index] != v3[index]) {
                identical = false
                break
            }
        }
        assertFalse(identical)
    }

    @Test
    fun phase2EchoesTrailingTwentyBytesAndRetainsKeyMessage() {
        val session = FairPlayHandshake()
        session.setup(phase1(0x03, 0))
        val request = phase2(0x03)

        val result = session.handshake(request)

        assertEquals(32, result.size)
        assertEquals(0x03, result[4].toInt() and 0xFF)
        for (index in 0 until 20) {
            assertEquals(index, result[12 + index].toInt() and 0xFF)
        }
        assertTrue(session.phase2Complete)
        assertArrayEquals(request, session.keyMessage)

        request[20] = 0x7F
        assertEquals(0, session.keyMessage?.get(20)?.toInt())
    }

    @Test
    fun newPhase1ClearsPreviouslyRetainedKeyMessage() {
        val session = FairPlayHandshake()
        session.handshake(phase2(0x03))
        session.setup(phase1(0x03, 1))

        assertFalse(session.phase2Complete)
        assertNull(session.keyMessage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMagicIsRejected() {
        FairPlayHandshake().setup(ByteArray(16))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unexpectedRequestSizeIsRejected() {
        FairPlayHandshake().respond(ByteArray(32))
    }
}

package com.atrishub.atriscast.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class RtspRequestParserTest {
    @Test
    fun parsesOptionsRequest() {
        val raw = "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: AirPlay/1.0\r\n\r\n"
        val request = RtspRequestParser.read(ByteArrayInputStream(raw.toByteArray()))

        assertEquals("OPTIONS", request?.method)
        assertEquals("*", request?.target)
        assertEquals("1", request?.cSeq)
        assertEquals(0, request?.body?.size)
    }

    @Test
    fun readsBodyUsingContentLength() {
        val raw = "POST /pair-setup RTSP/1.0\r\nCSeq: 2\r\nContent-Length: 4\r\n\r\ntest"
        val request = RtspRequestParser.read(ByteArrayInputStream(raw.toByteArray()))

        assertEquals("POST", request?.method)
        assertEquals("/pair-setup", request?.target)
        assertEquals("test", request?.body?.toString(Charsets.UTF_8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedBodyBeforeAllocation() {
        val raw = "POST /pair-setup RTSP/1.0\r\nContent-Length: 5000000\r\n\r\n"
        RtspRequestParser.read(ByteArrayInputStream(raw.toByteArray()))
    }

    @Test
    fun returnsNullForEmptyInput() {
        assertNull(RtspRequestParser.read(ByteArrayInputStream(ByteArray(0))))
    }
}

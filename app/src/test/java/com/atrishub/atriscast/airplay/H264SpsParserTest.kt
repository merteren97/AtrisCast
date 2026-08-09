package com.atrishub.atriscast.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class H264SpsParserTest {
    @Test
    fun parsesCodedMacroblockSizeAndVisibleCropSeparately() {
        // Real SPS published in a UxPlay AirPlay mirror debug trace. UxPlay reports the source as
        // 1440x1080; the H.264 bitstream is macroblock-coded at 1440x1088 and crops 8 lines.
        val sps = hex("27640028ac131450168089f966e020202040")

        val dimensions = H264SpsParser.parseDimensions(sps)

        assertNotNull(dimensions)
        assertEquals(1440, dimensions!!.codedWidth)
        assertEquals(1088, dimensions.codedHeight)
        assertEquals(1440, dimensions.visibleWidth)
        assertEquals(1080, dimensions.visibleHeight)
    }

    @Test
    fun rejectsNonSpsData() {
        assertNull(H264SpsParser.parseDimensions(byteArrayOf(0x65, 0x01, 0x02, 0x03)))
    }

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

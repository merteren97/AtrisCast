package com.atrishub.atriscast.airplay

import com.dd.plist.BinaryPropertyListWriter
import com.dd.plist.NSArray
import com.dd.plist.NSData
import com.dd.plist.NSDictionary
import com.dd.plist.NSNumber
import com.dd.plist.PropertyListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress

class AirPlaySetupSessionTest {
    private fun session(
        onMediaActivity: (Long) -> Unit = {},
        keyMessageProvider: () -> ByteArray? = { null },
        sessionKeyDecryptor: (ByteArray, ByteArray) -> Result<ByteArray> = { _, _ -> Result.success(ByteArray(16)) },
    ): AirPlaySetupSession =
        AirPlaySetupSession(
            remoteAddress = InetAddress.getLoopbackAddress(),
            keyMessageProvider = keyMessageProvider,
            surfaceProvider = { null },
            onMirrorStarted = {},
            onMediaActivity = onMediaActivity,
            onVideoFrameRendered = {},
            onVideoFormat = {},
            onMirrorError = {},
            onMirrorStopped = {},
            sessionKeyDecryptor = sessionKeyDecryptor,
        )

    @Test
    fun keySetupReturnsRealLocalTimingPort() {
        DatagramSocket(0).use { senderTiming ->
            session().use { session ->
                val request = NSDictionary().apply {
                    put("ekey", NSData(ByteArray(72) { 0x2A }))
                    put("eiv", NSData(ByteArray(16) { 0x11 }))
                    put("timingPort", senderTiming.localPort.toLong())
                    put("timingProtocol", "NTP")
                }

                val result = session.respond(BinaryPropertyListWriter.writeToArray(request))
                val response = PropertyListParser.parse(result.body) as NSDictionary

                assertEquals(0L, (response.objectForKey("eventPort") as NSNumber).longValue())
                assertTrue((response.objectForKey("timingPort") as NSNumber).longValue() > 0L)
                assertTrue(result.summary.contains("timing UDP"))
                assertEquals(72, session.encryptedStreamKey?.size)
                assertEquals(16, session.encryptionIv?.size)
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun mirrorSetupRejectsSessionBeforeFairPlayKeyExchangeCompletes() {
        session().use { session ->
            val stream = NSDictionary().apply {
                put("type", 110L)
                put("streamConnectionID", 123456L)
            }
            val request = NSDictionary().apply {
                put("streams", NSArray(stream))
            }

            session.respond(BinaryPropertyListWriter.writeToArray(request))
        }
    }

    @Test
    fun audioSetupReturnsUdpDataAndControlPorts() {
        session(
            keyMessageProvider = { ByteArray(164) { 0x33 } },
            sessionKeyDecryptor = { _, _ -> Result.success(ByteArray(16) { 0x44 }) },
        ).use { session ->
            val stream = NSDictionary().apply {
                put("type", 96L)
                put("ct", 8L)
                put("spf", 480L)
                put("controlPort", 0L)
            }
            val request = NSDictionary().apply {
                put("ekey", NSData(ByteArray(72) { 0x2A }))
                put("eiv", NSData(ByteArray(16) { 0x11 }))
                put("streams", NSArray(stream))
            }

            val result = session.respond(BinaryPropertyListWriter.writeToArray(request))
            val response = PropertyListParser.parse(result.body) as NSDictionary
            val streams = response.objectForKey("streams") as NSArray
            val audio = streams.objectAtIndex(0) as NSDictionary

            assertEquals(96L, (audio.objectForKey("type") as NSNumber).longValue())
            assertTrue((audio.objectForKey("dataPort") as NSNumber).longValue() > 0L)
            assertTrue((audio.objectForKey("controlPort") as NSNumber).longValue() > 0L)
            assertTrue(result.summary.contains("ct=8"))
            assertTrue(result.summary.contains("spf=480"))
        }
    }
}

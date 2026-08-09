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
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AirPlaySetupSessionTest {
    @Test
    fun keySetupReturnsRealLocalTimingPort() {
        DatagramSocket(0).use { senderTiming ->
            AirPlaySetupSession(InetAddress.getLoopbackAddress()) { }.use { session ->
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

    @Test
    fun mirrorSetupReturnsTcpPortAndDetectsIncomingMediaBytes() {
        val mediaArrived = CountDownLatch(1)
        AirPlaySetupSession(InetAddress.getLoopbackAddress()) { bytes ->
            if (bytes > 0) mediaArrived.countDown()
        }.use { session ->
            val stream = NSDictionary().apply {
                put("type", 110L)
                put("streamConnectionID", 123456L)
            }
            val request = NSDictionary().apply {
                put("streams", NSArray(stream))
            }

            val result = session.respond(BinaryPropertyListWriter.writeToArray(request))
            val response = PropertyListParser.parse(result.body) as NSDictionary
            val streams = response.objectForKey("streams") as NSArray
            val mirror = streams.objectAtIndex(0) as NSDictionary
            val dataPort = (mirror.objectForKey("dataPort") as NSNumber).longValue().toInt()

            assertEquals(110L, (mirror.objectForKey("type") as NSNumber).longValue())
            assertTrue(dataPort > 0)

            Socket(InetAddress.getLoopbackAddress(), dataPort).use { client ->
                client.getOutputStream().write(byteArrayOf(1, 2, 3, 4, 5))
                client.getOutputStream().flush()
            }
            assertTrue("mirror listener should observe media bytes", mediaArrived.await(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun audioSetupReturnsUdpDataAndControlPorts() {
        AirPlaySetupSession(InetAddress.getLoopbackAddress()) { }.use { session ->
            val stream = NSDictionary().apply { put("type", 96L) }
            val request = NSDictionary().apply { put("streams", NSArray(stream)) }

            val result = session.respond(BinaryPropertyListWriter.writeToArray(request))
            val response = PropertyListParser.parse(result.body) as NSDictionary
            val streams = response.objectForKey("streams") as NSArray
            val audio = streams.objectAtIndex(0) as NSDictionary

            assertEquals(96L, (audio.objectForKey("type") as NSNumber).longValue())
            assertTrue((audio.objectForKey("dataPort") as NSNumber).longValue() > 0L)
            assertTrue((audio.objectForKey("controlPort") as NSNumber).longValue() > 0L)
        }
    }
}

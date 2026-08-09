package com.atrishub.atriscast.airplay

import com.dd.plist.NSDictionary
import com.dd.plist.NSNumber
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPlayProtocolTest {
    @Test
    fun advertisedProfileDoesNotClaimLegacyPairing() {
        assertFalse(AirPlayProfile.supportsLegacyPairing())
        assertEquals("0x527FFEE6,0x0", AirPlayProfile.DISCOVERY_FEATURES)
    }

    @Test
    fun infoResponseIsBinaryPlistWithReceiverIdentity() {
        val payload = AirPlayInfoResponder(
            displayName = "AtrisCast",
            deviceId = "02:11:22:33:44:55",
            persistentId = "11111111-2222-3333-4444-555555555555",
        ).createResponse()

        assertTrue(payload.copyOfRange(0, 8).toString(Charsets.US_ASCII).startsWith("bplist"))

        val root = PropertyListParser.parse(payload) as NSDictionary
        assertEquals("AtrisCast", (root.objectForKey("name") as NSString).content)
        assertEquals("02:11:22:33:44:55", (root.objectForKey("deviceID") as NSString).content)
        assertEquals(AirPlayProfile.MODEL, (root.objectForKey("model") as NSString).content)
        assertEquals(AirPlayProfile.FEATURES_LOW, (root.objectForKey("features") as NSNumber).longValue())
    }
}

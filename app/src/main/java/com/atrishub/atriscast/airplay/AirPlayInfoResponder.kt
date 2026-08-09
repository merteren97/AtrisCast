package com.atrishub.atriscast.airplay

import com.dd.plist.BinaryPropertyListWriter
import com.dd.plist.NSArray
import com.dd.plist.NSDictionary

/** Builds the binary property-list returned by GET /info during AirPlay negotiation. */
class AirPlayInfoResponder(
    private val displayName: String,
    private val deviceId: String,
    private val persistentId: String,
) {
    fun createResponse(): ByteArray {
        val root = NSDictionary().apply {
            put("deviceID", deviceId)
            put("macAddress", deviceId)
            put("features", AirPlayProfile.FEATURES_LOW)
            put("name", displayName)
            put("model", AirPlayProfile.MODEL)
            put("sourceVersion", AirPlayProfile.SOURCE_VERSION)
            put("pi", persistentId)
            put("vv", AirPlayProfile.PROTOCOL_VERSION)
            put("statusFlags", AirPlayProfile.STATUS_FLAGS)
            put("keepAliveLowPower", 1L)
            put("keepAliveSendStatsAsBody", true)
            put("initialVolume", -20.0)
            put("audioFormats", audioFormats())
            put("audioLatencies", audioLatencies())
            put("displays", displays())
        }
        return BinaryPropertyListWriter.writeToArray(root)
    }

    private fun audioFormats() = NSArray(
        audioFormat(100L),
        audioFormat(101L),
    )

    private fun audioFormat(type: Long) = NSDictionary().apply {
        put("type", type)
        put("audioInputFormats", 0x03FFFFFCL)
        put("audioOutputFormats", 0x03FFFFFCL)
    }

    private fun audioLatencies() = NSArray(
        audioLatency(100L),
        audioLatency(101L),
    )

    private fun audioLatency(type: Long) = NSDictionary().apply {
        put("type", type)
        put("audioType", "default")
        put("inputLatencyMicros", 0L)
        put("outputLatencyMicros", 0L)
    }

    private fun displays() = NSArray(
        NSDictionary().apply {
            put("uuid", persistentId)
            put("widthPhysical", 0L)
            put("heightPhysical", 0L)
            put("width", AirPlayProfile.DISPLAY_WIDTH)
            put("height", AirPlayProfile.DISPLAY_HEIGHT)
            put("widthPixels", AirPlayProfile.DISPLAY_WIDTH)
            put("heightPixels", AirPlayProfile.DISPLAY_HEIGHT)
            put("rotation", false)
            put("refreshRate", AirPlayProfile.DISPLAY_REFRESH_RATE)
            put("maxFPS", AirPlayProfile.DISPLAY_MAX_FPS)
            put("overscanned", false)
            put("features", 14L)
        }
    )
}

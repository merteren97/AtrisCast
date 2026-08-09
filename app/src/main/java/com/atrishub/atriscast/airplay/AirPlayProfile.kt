package com.atrishub.atriscast.airplay

/**
 * AirPlay compatibility profile advertised by AtrisCast.
 *
 * Pairing support is intentionally not advertised until a complete pairing implementation exists.
 * Keeping the capability surface honest prevents iOS from entering /pair-setup and receiving an
 * unsupported response.
 */
object AirPlayProfile {
    const val AIRPLAY_PORT = 7000
    const val AIRPLAY_TYPE = "_airplay._tcp"
    const val RAOP_TYPE = "_raop._tcp"

    // Legacy mirroring profile with bit 27 (Supports Legacy Pairing) disabled.
    const val FEATURES_LOW: Long = 0x527FFEE6L
    const val FEATURES_HIGH: Long = 0x0L
    const val DISCOVERY_FEATURES = "0x527FFEE6,0x0"

    const val MODEL = "AppleTV3,2"
    const val SOURCE_VERSION = "220.68"
    const val FLAGS = "0x4"
    const val STATUS_FLAGS: Long = 68L
    const val PROTOCOL_VERSION: Long = 2L

    const val DISPLAY_WIDTH: Long = 1920L
    const val DISPLAY_HEIGHT: Long = 1080L
    const val DISPLAY_MAX_FPS: Long = 30L

    // Legacy Apple TV profiles advertise the duration of one refresh, not the frequency in Hz.
    // UxPlay sends 1 / 60 for a 60 Hz display; sending 60.0 here changes sender pacing semantics.
    const val DISPLAY_REFRESH_RATE = 1.0 / 60.0

    fun supportsLegacyPairing(): Boolean = (FEATURES_LOW and (1L shl 27)) != 0L
}

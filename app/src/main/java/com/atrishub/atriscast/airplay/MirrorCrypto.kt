package com.atrishub.atriscast.airplay

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Cryptographic helpers for the legacy AirPlay type-110 screen-mirroring stream. */
object MirrorCrypto {
    private val startCode = byteArrayOf(0, 0, 0, 1)

    /**
     * Builds the continuous AES-128-CTR cipher used by the mirror video stream.
     *
     * AirPlay first derives an effective AES key from the FairPlay-decrypted session key and the
     * pair-verify ECDH secret. AtrisCast's current legacy profile does not negotiate pair-verify yet,
     * so [ecdhSecret] is empty, but the SHA-512 derivation step must still be performed.
     */
    fun createVideoCipher(
        fairPlayKey: ByteArray,
        streamConnectionId: Long,
        ecdhSecret: ByteArray = ByteArray(0),
    ): Cipher {
        require(fairPlayKey.size == 16) { "FairPlay session key must be 16 bytes" }
        val effectiveKey = sha512(fairPlayKey + ecdhSecret).copyOf(16)
        val unsignedId = java.lang.Long.toUnsignedString(streamConnectionId)
        val key = sha512(
            "AirPlayStreamKey$unsignedId".toByteArray(Charsets.US_ASCII) + effectiveKey
        ).copyOf(16)
        val iv = sha512(
            "AirPlayStreamIV$unsignedId".toByteArray(Charsets.US_ASCII) + effectiveKey
        ).copyOf(16)
        return Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }
    }

    /** Converts 4-byte big-endian AVCC length-prefixed NAL units into Annex-B framing. */
    fun avccToAnnexB(payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(payload.size + 32)
        var offset = 0
        while (offset + 4 <= payload.size) {
            val length = ((payload[offset].toInt() and 0xFF) shl 24) or
                ((payload[offset + 1].toInt() and 0xFF) shl 16) or
                ((payload[offset + 2].toInt() and 0xFF) shl 8) or
                (payload[offset + 3].toInt() and 0xFF)
            offset += 4
            if (length <= 0 || offset + length > payload.size) break
            output.write(startCode)
            output.write(payload, offset, length)
            offset += length
        }
        return output.toByteArray()
    }

    fun withStartCode(nal: ByteArray): ByteArray = startCode + nal

    private fun sha512(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(input)
}

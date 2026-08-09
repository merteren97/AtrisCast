package com.atrishub.atriscast.airplay

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Cryptographic helpers for the legacy AirPlay type-110 screen-mirroring stream. */
object MirrorCrypto {
    data class AvccFrame(
        val annexB: ByteArray,
        val nalTypes: List<Int>,
    )

    private val startCode = byteArrayOf(0, 0, 0, 1)

    /**
     * Builds the continuous AES-128-CTR cipher used by the mirror video stream.
     *
     * The FairPlay-decrypted 16-byte session key is used directly when legacy pairing was not
     * negotiated. If pairing did establish a 32-byte X25519 shared secret, the session key is first
     * SHA-512 hashed together with that secret and truncated to 16 bytes before the stream key/IV
     * derivation. AtrisCast currently advertises legacy pairing as disabled, so its normal path uses
     * the FairPlay key directly.
     */
    fun createVideoCipher(
        fairPlayKey: ByteArray,
        streamConnectionId: Long,
        ecdhSecret: ByteArray = ByteArray(0),
    ): Cipher {
        require(fairPlayKey.size == 16) { "FairPlay session key must be 16 bytes" }
        require(ecdhSecret.isEmpty() || ecdhSecret.size == 32) {
            "AirPlay ECDH secret must be 32 bytes when pairing is active"
        }

        val effectiveKey = if (ecdhSecret.isEmpty()) {
            fairPlayKey
        } else {
            sha512(fairPlayKey + ecdhSecret).copyOf(16)
        }
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
            val length = bigEndianInt(payload, offset)
            offset += 4
            if (length <= 0 || offset + length > payload.size) break
            output.write(startCode)
            output.write(payload, offset, length)
            offset += length
        }
        return output.toByteArray()
    }

    /**
     * Strict AVCC parser used on live decrypted video packets.
     *
     * The legacy reference receiver rejects a decrypted packet when a NAL length runs past the
     * payload or when the H.264 forbidden_zero_bit is set. Keeping this strict path separate from
     * [avccToAnnexB] lets diagnostics distinguish "no keyframe yet" from "the AES output is not
     * H.264 at all" instead of silently discarding malformed decrypted bytes forever.
     */
    fun parseAvccFrame(payload: ByteArray): AvccFrame? {
        if (payload.size < 5) return null

        val output = ByteArrayOutputStream(payload.size + 32)
        val nalTypes = mutableListOf<Int>()
        var offset = 0

        while (offset < payload.size) {
            if (offset + 4 > payload.size) return null
            val length = bigEndianInt(payload, offset)
            offset += 4
            if (length <= 0 || offset + length > payload.size) return null

            val header = payload[offset].toInt() and 0xFF
            if ((header and 0x80) != 0) return null
            nalTypes += header and 0x1F

            output.write(startCode)
            output.write(payload, offset, length)
            offset += length
        }

        if (offset != payload.size || nalTypes.isEmpty()) return null
        return AvccFrame(output.toByteArray(), nalTypes)
    }

    fun withStartCode(nal: ByteArray): ByteArray = startCode + nal

    private fun bigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun sha512(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(input)
}

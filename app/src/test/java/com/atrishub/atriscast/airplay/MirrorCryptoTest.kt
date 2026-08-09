package com.atrishub.atriscast.airplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class MirrorCryptoTest {
    @Test
    fun avccPayloadIsConvertedToAnnexB() {
        val first = byteArrayOf(0x65, 0x11, 0x22)
        val second = byteArrayOf(0x41, 0x33)
        val payload = lengthPrefix(first) + first + lengthPrefix(second) + second

        val result = MirrorCrypto.avccToAnnexB(payload)

        assertArrayEquals(
            byteArrayOf(0, 0, 0, 1) + first + byteArrayOf(0, 0, 0, 1) + second,
            result,
        )
    }

    @Test
    fun malformedTrailingNalIsIgnoredWithoutCorruptingPriorNal() {
        val valid = byteArrayOf(0x65, 0x01)
        val payload = lengthPrefix(valid) + valid + byteArrayOf(0, 0, 0, 8, 0x41)

        val result = MirrorCrypto.avccToAnnexB(payload)

        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + valid, result)
    }

    @Test
    fun strictAvccParserReportsNalTypesForValidDecryptedFrame() {
        val idr = byteArrayOf(0x65, 0x11, 0x22)
        val predicted = byteArrayOf(0x41, 0x33)
        val payload = lengthPrefix(idr) + idr + lengthPrefix(predicted) + predicted

        val parsed = MirrorCrypto.parseAvccFrame(payload)
            ?: throw AssertionError("Expected a valid AVCC frame")

        assertEquals(listOf(5, 1), parsed.nalTypes)
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 1) + idr + byteArrayOf(0, 0, 0, 1) + predicted,
            parsed.annexB,
        )
    }

    @Test
    fun strictAvccParserRejectsMalformedDecryptedFrame() {
        val payload = byteArrayOf(0, 0, 0, 8, 0x65, 0x01)

        assertNull(MirrorCrypto.parseAvccFrame(payload))
    }

    @Test
    fun strictAvccParserRejectsForbiddenZeroBit() {
        val invalidNal = byteArrayOf(0xE5.toByte(), 0x01)
        val payload = lengthPrefix(invalidNal) + invalidNal

        assertNull(MirrorCrypto.parseAvccFrame(payload))
    }

    @Test
    fun unpairedVideoCipherUsesFairPlayKeyDirectly() {
        val key = ByteArray(16) { it.toByte() }
        val payload = ByteArray(48) { (it * 3).toByte() }

        val result = MirrorCrypto.createVideoCipher(key, 0x1234_5678L).update(payload)

        assertArrayEquals(
            hex("0b0d5844dee90d1eeb41d10e8b98b3d32d51f44deb49e060d541785ec09cf396741bf0745d53f3b0025d0a3f9a540851"),
            result,
        )
    }

    @Test
    fun pairedVideoCipherMixesTheEcdhSecretBeforeStreamDerivation() {
        val key = ByteArray(16) { it.toByte() }
        val ecdhSecret = ByteArray(32) { (it + 32).toByte() }
        val payload = ByteArray(48) { (it * 3).toByte() }

        val result = MirrorCrypto.createVideoCipher(key, 0x1234_5678L, ecdhSecret).update(payload)

        assertArrayEquals(
            hex("7b55964e0aeba8f63986bef0f8c100c9256e7e0d8db0797c0dcdf39581ecf8277243ce2bdb059a47e21ff34298545e7d"),
            result,
        )
    }

    @Test
    fun videoCipherRejectsInvalidEcdhSecretLength() {
        try {
            MirrorCrypto.createVideoCipher(ByteArray(16), 1L, ByteArray(31))
            fail("Expected an invalid ECDH secret length to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun lengthPrefix(bytes: ByteArray): ByteArray = byteArrayOf(
        ((bytes.size ushr 24) and 0xFF).toByte(),
        ((bytes.size ushr 16) and 0xFF).toByte(),
        ((bytes.size ushr 8) and 0xFF).toByte(),
        (bytes.size and 0xFF).toByte(),
    )

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

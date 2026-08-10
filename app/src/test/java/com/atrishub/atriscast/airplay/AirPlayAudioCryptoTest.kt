package com.atrishub.atriscast.airplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AirPlayAudioCryptoTest {
    @Test
    fun decryptsWholeCbcBlocksAndPreservesTrailingBytes() {
        val key = ByteArray(16) { index -> (index * 3 + 1).toByte() }
        val iv = ByteArray(16) { index -> (0x40 + index).toByte() }
        val plain = ByteArray(37) { index -> (index * 7 + 5).toByte() }
        val encryptedLength = 32

        val cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }
        val encrypted = cipher.doFinal(plain, 0, encryptedLength) +
            plain.copyOfRange(encryptedLength, plain.size)

        assertArrayEquals(plain, AirPlayAudioCrypto.decryptPayload(key, iv, encrypted))
    }

    @Test
    fun payloadShorterThanOneAesBlockIsUnchanged() {
        val key = ByteArray(16) { 0x11 }
        val iv = ByteArray(16) { 0x22 }
        val payload = byteArrayOf(0x00, 0x68, 0x34, 0x00)

        assertArrayEquals(payload, AirPlayAudioCrypto.decryptPayload(key, iv, payload))
    }

    @Test
    fun airPlayAacEld480UsesExtendedObjectType39Config() {
        assertArrayEquals(
            byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x50, 0x00),
            AirPlayAacEldConfig.build(480),
        )
    }

    @Test
    fun aacEld512ClearsFrameLengthFlag() {
        assertArrayEquals(
            byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x40, 0x00),
            AirPlayAacEldConfig.build(512),
        )
    }

    @Test
    fun redundantAacRtpPatternAcceptsEachSequenceOnlyOnce() {
        val deduplicator = RtpSequenceDeduplicator(capacity = 8)
        val pattern = intArrayOf(0, 0, 1, 0, 1, 2, 1, 2, 3)
        val accepted = pattern.filter { deduplicator.shouldAccept(it) }.toIntArray()

        assertArrayEquals(intArrayOf(0, 1, 2, 3), accepted)
    }

    @Test
    fun sequenceWindowAllowsRtpWrapAfterOldEntriesExpire() {
        val deduplicator = RtpSequenceDeduplicator(capacity = 4)

        assertTrue(deduplicator.shouldAccept(0xFFFE))
        assertTrue(deduplicator.shouldAccept(0xFFFF))
        assertTrue(deduplicator.shouldAccept(0))
        assertTrue(deduplicator.shouldAccept(1))
        assertFalse(deduplicator.shouldAccept(0))

        assertTrue(deduplicator.shouldAccept(2))
        assertTrue(deduplicator.shouldAccept(0xFFFE))
    }
}

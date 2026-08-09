package com.atrishub.atriscast.airplay

import org.junit.Assert.assertArrayEquals
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
}

package com.atrishub.atriscast.airplay

import org.junit.Assert.assertArrayEquals
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
    fun legacyVideoCipherMatchesAirPlayKdfRegressionVector() {
        val key = ByteArray(16) { it.toByte() }
        val payload = ByteArray(48) { (it * 3).toByte() }

        val result = MirrorCrypto.createVideoCipher(key, 0x1234_5678L).update(payload)

        assertArrayEquals(
            hex("f41089f1bbdd1f2ac7077e8212695b2e6293fec5d4ff0cca485f9c72ad75a07840e80f8d39a43f406c03f009d14f12db"),
            result,
        )
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

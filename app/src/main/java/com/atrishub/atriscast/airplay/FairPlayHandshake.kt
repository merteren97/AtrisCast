package com.atrishub.atriscast.airplay

/**
 * Per-control-connection FairPlay setup state for the legacy AirPlay mirroring handshake.
 *
 * Only fp-setup phase 1/2 lives here. Stream-key decryption is intentionally NOT implemented
 * in this Apache-licensed module because the commonly used PlayFair native implementation is GPL.
 *
 * Phase reply data and protocol behavior are adapted from PhairPlay (Apache-2.0), which in turn
 * documents RPiPlay/ShairPlay as protocol references.
 */
class FairPlayHandshake {
    var negotiatedVersion: Int = 0
        private set

    var phase2Complete: Boolean = false
        private set

    fun respond(request: ByteArray): ByteArray = when (request.size) {
        PHASE1_SIZE -> setup(request)
        PHASE2_SIZE -> handshake(request)
        else -> throw IllegalArgumentException(
            "unexpected fp-setup size ${request.size}; expected $PHASE1_SIZE or $PHASE2_SIZE"
        )
    }

    fun setup(request: ByteArray): ByteArray {
        require(request.size == PHASE1_SIZE) {
            "fp-setup phase 1 expects $PHASE1_SIZE bytes, got ${request.size}"
        }
        validateMagic(request)

        val version = request[4].toInt() and 0xFF
        val mode = request[14].toInt() and 0xFF
        require(mode in 0..3) { "invalid fp-setup mode $mode" }

        negotiatedVersion = version
        phase2Complete = false

        return when (version) {
            0x03 -> REPLIES_V3[mode].copyOf()
            0x02 -> REPLY_V2.copyOf().also { it[13] = request[14] }
            else -> throw IllegalArgumentException("unsupported FairPlay version $version")
        }
    }

    fun handshake(request: ByteArray): ByteArray {
        require(request.size == PHASE2_SIZE) {
            "fp-setup phase 2 expects $PHASE2_SIZE bytes, got ${request.size}"
        }
        validateMagic(request)

        val version = request[4].toInt() and 0xFF
        require(version == 0x02 || version == 0x03) {
            "unsupported FairPlay version $version"
        }
        if (negotiatedVersion != 0) {
            require(version == negotiatedVersion) {
                "FairPlay version changed within the same connection"
            }
        } else {
            negotiatedVersion = version
        }

        phase2Complete = true
        return FP_HEADER.copyOf().also { it[4] = request[4] } +
            request.copyOfRange(PHASE2_ECHO_OFFSET, PHASE2_SIZE)
    }

    private fun validateMagic(request: ByteArray) {
        require(
            request.size >= 4 &&
                request[0] == 0x46.toByte() &&
                request[1] == 0x50.toByte() &&
                request[2] == 0x4C.toByte() &&
                request[3] == 0x59.toByte()
        ) { "invalid fp-setup magic" }
    }

    companion object {
        const val PHASE1_SIZE = 16
        const val PHASE2_SIZE = 164
        private const val PHASE2_ECHO_OFFSET = 144

        private fun hex(value: String): ByteArray =
            ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }

        private val FP_HEADER = hex("46504c590301040000000014")

        private val REPLIES_V3 = arrayOf(
            hex("46504c59030102000000008202000f9f3f9e0a2521dbdf312ab2bfb29e8d232b6376a8c818701d22ae93d82737feaf9db4fdf41c2dba9d1f49caaabf6591ac1f7bc6f7e0663d21afe01565953eab81f418ceed095adb7c3d0e254909a79831d49c3982973434facb42c63a1cd911a6fe941a8a6d4a743b46c3a7649e44c78955e49d8155009549c4e2f7a3f6d5ba"),
            hex("46504c5903010200000000820201cf32a25714b2524f8aa0ad7af164e37bcf4424e200047efc0ad67afcd95ded1c2730bb591b962ed63a9c4ded88ba8fc78de64d91ccfd5c7b56da88e31f5cceafc7431995a01665a54e1939d25b94db64b9e45d8d063e1e6af07e9656162b0efa404275ea5a44d9591c7256b9fbe6513898b80227721988571650942ad946688a"),
            hex("46504c5903010200000000820202c169a352eeed35b18cdd9c58d64f16c1519a89eb5317bd0d4336cd68f638ff9d016a5b52b7fa9216b2b65482c78444118121a2c7fed83db7119e9182aad7d18c7063e2a457555910af9e0efc76347d164043807f581ee4fbe42ca9dedc1b5eb2a3aa3d2ecd59e7eee70b3629f22afd161d877353ddb99adc8e07006e56f850ce"),
            hex("46504c59030102000000008202039001e1727e0f57f9f5880db104a6257a23f5cfff1abbe1e93045251afb97eb9fc0011ebe0f3a81df5b691d76acb2f7a5c708e3d328f56bb39dbde5f29c8a17f481487e3ae863c678325422e6f78e166d18aa7fd636258bce28726f661f738893ce44311e4be6c0535193e5ef72e8686233729c227d820c999445d89246c8c359")
        )

        private val REPLY_V2 = hex(
            "46504c59020102000000008202022f7b69e6b27ebbf0685f98547f37cecf8706996e7e6b0fb2fa712053e39483da22c783a072404ddd41aa3d4c6e302255aaa2da1eb477838c79d56517c3fa0154339ee3829f30f0a48f76df77117e569ef395e8e213b31eb670ec5a8af26afcbc8931e67ee8b9c5f2c71d78f3ef8d61f73bcc17c34023524a8b9cb1750566e6b3"
        )
    }
}

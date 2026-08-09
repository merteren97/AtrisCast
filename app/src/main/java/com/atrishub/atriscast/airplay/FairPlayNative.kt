package com.atrishub.atriscast.airplay

/**
 * JNI boundary for the optional LGPL FairPlay key-decryption component.
 *
 * The Android/Kotlin receiver remains Apache-2.0. The native bridge is built as a separate shared
 * library from the pinned LGPL-3.0-or-later shairplay-rust implementation; see THIRD_PARTY_NOTICES.md.
 */
object FairPlayNative {
    private val loadResult: Result<Unit> by lazy {
        runCatching { System.loadLibrary("atriscast_fairplay") }
    }

    val isAvailable: Boolean
        get() = loadResult.isSuccess

    fun decryptSessionKey(keyMessage: ByteArray, encryptedKey: ByteArray): Result<ByteArray> {
        if (keyMessage.size != KEY_MESSAGE_SIZE) {
            return Result.failure(IllegalArgumentException("FairPlay key message must be $KEY_MESSAGE_SIZE bytes"))
        }
        if (encryptedKey.size != ENCRYPTED_KEY_SIZE) {
            return Result.failure(IllegalArgumentException("FairPlay encrypted key must be $ENCRYPTED_KEY_SIZE bytes"))
        }
        loadResult.exceptionOrNull()?.let {
            return Result.failure(IllegalStateException("FairPlay native bridge is unavailable", it))
        }
        return runCatching {
            nativeDecryptSessionKey(keyMessage, encryptedKey).also { result ->
                require(result.size == DECRYPTED_KEY_SIZE) {
                    "FairPlay native bridge returned ${result.size} bytes instead of $DECRYPTED_KEY_SIZE"
                }
            }
        }
    }

    @JvmStatic
    private external fun nativeDecryptSessionKey(keyMessage: ByteArray, encryptedKey: ByteArray): ByteArray

    private const val KEY_MESSAGE_SIZE = 164
    private const val ENCRYPTED_KEY_SIZE = 72
    private const val DECRYPTED_KEY_SIZE = 16
}

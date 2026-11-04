package com.v2ray.ang.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM sealing for locked configs: [nonce 12 bytes][ciphertext + 16-byte tag].
 * Pure JVM so it is unit-testable; base64 and key handling live in ConfigLockManager.
 */
object ConfigLock {
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    const val KEY_LEN = 32

    fun seal(key: ByteArray, plain: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(key.size == KEY_LEN)
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return nonce + cipher.doFinal(plain)
    }

    /** Null when the data was not sealed with [key] or was modified. */
    fun open(key: ByteArray, sealed: ByteArray): ByteArray? {
        if (key.size != KEY_LEN || sealed.size <= NONCE_LEN) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_LEN)
            )
            cipher.doFinal(sealed, NONCE_LEN, sealed.size - NONCE_LEN)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reverses the byte mask applied by app/build.gradle.kts to the build-time key, so the key
     * does not sit in the APK as a plain hex string. Null when no key was built in.
     */
    fun unmaskKey(maskedHex: String): ByteArray? {
        if (maskedHex.length != KEY_LEN * 2) return null
        val key = ByteArray(KEY_LEN)
        for (i in 0 until KEY_LEN) {
            val b = maskedHex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
            key[i] = (b xor maskByte(i)).toByte()
        }
        return key
    }

    /** Must match the mask in app/build.gradle.kts. */
    fun maskByte(i: Int): Int = (0xA5 + i * 7) and 0xff
}

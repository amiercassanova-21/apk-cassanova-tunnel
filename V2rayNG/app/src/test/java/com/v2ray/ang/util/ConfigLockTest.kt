package com.v2ray.ang.util

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfigLockTest {

    private val key = ByteArray(ConfigLock.KEY_LEN) { (it * 3 + 1).toByte() }
    private val link = "vless://uuid@host.example.com:443?type=ws&security=tls#Admin".toByteArray()

    @Test
    fun sealThenOpenReturnsTheLink() {
        val sealed = ConfigLock.seal(key, link)
        assertArrayEquals(link, ConfigLock.open(key, sealed))
    }

    @Test
    fun sameLinkSealsDifferentlyEachTime() {
        assert(!ConfigLock.seal(key, link).contentEquals(ConfigLock.seal(key, link)))
    }

    @Test
    fun wrongKeyTamperingAndGarbageAreRejected() {
        val sealed = ConfigLock.seal(key, link)
        val otherKey = key.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertNull(ConfigLock.open(otherKey, sealed))
        val tampered = sealed.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertNull(ConfigLock.open(key, tampered))
        assertNull(ConfigLock.open(key, ByteArray(5)))
        assertNull(ConfigLock.open(ByteArray(3), sealed))
    }

    @Test
    fun unmaskReversesTheGradleMask() {
        // Same formula as app/build.gradle.kts (maskedLockKey).
        val hex = key.joinToString("") { "%02x".format(it) }
        val masked = hex.chunked(2).mapIndexed { i, b ->
            ((b.toInt(16) xor (0xA5 + i * 7)) and 0xff).toString(16).padStart(2, '0')
        }.joinToString("")
        assertArrayEquals(key, ConfigLock.unmaskKey(masked))
        assertNull(ConfigLock.unmaskKey(""))
        assertNull(ConfigLock.unmaskKey("zz".repeat(32)))
        assertEquals(0xA5, ConfigLock.maskByte(0))
    }
}

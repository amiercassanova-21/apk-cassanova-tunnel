package com.v2ray.ang.core

import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoPingTest {

    @Test
    fun defaultsAreResetAutoFiveSecondsThreeFailures() {
        val c = AutoPing.Config()
        assertEquals(AutoPing.Mode.RESET, c.mode)
        assertEquals(AutoPing.Method.AUTO, c.method)
        assertEquals(AutoPing.Method.AUTO, AutoPing.Method.from(null))
        assertEquals(AutoPing.Method.AUTO, AutoPing.Method.from("bogus"))
        assertEquals(AutoPing.Method.STUN, AutoPing.Method.from("stun"))
        assertEquals(AutoPing.Method.HTTP, AutoPing.Method.from("http"))
        assertEquals(5, c.effectiveIntervalSec)
        assertEquals(3, c.effectiveFailLimit)
        assertTrue(c.resets)
        assertEquals(AutoPing.Mode.RESET, AutoPing.Mode.from(null))
    }

    @Test
    fun aggressiveTightensIntervalAndLimit() {
        val c = AutoPing.Config(mode = AutoPing.Mode.AGGRESSIVE, intervalSec = 5, failLimit = 3)
        assertEquals(2, c.effectiveIntervalSec)
        assertEquals(2, c.effectiveFailLimit)
        assertFalse(AutoPing.Config(mode = AutoPing.Mode.KEEPALIVE).resets)
    }

    @Test
    fun targetParsing() {
        assertEquals("stun.cloudflare.com" to 3478, AutoPing.parseTarget("stun.cloudflare.com:3478"))
        assertEquals("stun.l.google.com" to 3478, AutoPing.parseTarget("stun.l.google.com"))
        assertEquals(null, AutoPing.parseTarget(" "))
        assertEquals(null, AutoPing.parseTarget("host:abc"))
    }

    @Test
    fun stunRequestAndResponse() {
        val txn = ByteArray(12) { it.toByte() }
        val req = AutoPing.stunBindingRequest(txn)
        assertEquals(20, req.size)
        assertEquals(0x21, req[4].toInt() and 0xff)
        val resp = req.copyOf()
        resp[0] = 0x01; resp[1] = 0x01
        assertTrue(AutoPing.isStunSuccess(resp, resp.size, txn))
        assertFalse(AutoPing.isStunSuccess(req, req.size, txn))
        val other = resp.copyOf(); other[19] = 99
        assertFalse(AutoPing.isStunSuccess(other, other.size, txn))
    }

    @Test
    fun socksUdpFraming() {
        val p = AutoPing.socksUdpPacket("a.b", 3478, byteArrayOf(9))
        assertEquals(listOf(0, 0, 0, 3, 3), p.take(5).map { it.toInt() })
        assertEquals(9, p.last().toInt())
        val ipv4 = byteArrayOf(0, 0, 0, 1, 1, 2, 3, 4, 0x0d, 0x96.toByte(), 42)
        assertEquals(10, AutoPing.socksUdpDataOffset(ipv4, ipv4.size))
        assertEquals(p.size - 1, AutoPing.socksUdpDataOffset(p, p.size))
        assertEquals(-1, AutoPing.socksUdpDataOffset(byteArrayOf(0, 0, 1, 1), 4))
    }

    @Test
    fun failureCounterResetsAfterLimit() {
        val c = AutoPing.FailureCounter(3)
        assertFalse(c.record(false)); assertFalse(c.record(false))
        assertTrue(c.record(false))
        assertEquals(0, c.consecutive)
        assertFalse(c.record(false)); assertFalse(c.record(true)); assertEquals(0, c.consecutive)
    }

    @Test
    fun logThrottle() {
        assertTrue(AutoPing.shouldLog(null, 70, 0))
        assertFalse(AutoPing.shouldLog(70, 75, 5_000))
        assertTrue(AutoPing.shouldLog(70, 120, 5_000))
        assertTrue(AutoPing.shouldLog(70, -1, 5_000))
        assertTrue(AutoPing.shouldLog(-1, 70, 5_000))
        assertTrue(AutoPing.shouldLog(70, 72, 60_000))
    }

    @Test
    fun udpRetryOnlyAfterFallbackAndInterval() {
        assertFalse(AutoPing.shouldRetryUdp(fellBack = false, lastTryAt = 0, now = 10_000_000))
        assertFalse(AutoPing.shouldRetryUdp(fellBack = true, lastTryAt = 1_000, now = 1_000 + AutoPing.UDP_RETRY_MS - 1))
        assertTrue(AutoPing.shouldRetryUdp(fellBack = true, lastTryAt = 1_000, now = 1_000 + AutoPing.UDP_RETRY_MS))
    }

    private fun custom(vararg protocols: String): String =
        """{"outbounds":[${protocols.joinToString(",") { """{"protocol":"$it"}""" }}]}"""

    @Test
    fun automaticMethodFollowsTheConfig() {
        listOf(EConfigType.SSH, EConfigType.HYSTERIA2).forEach { assertTrue(AutoPing.carriesUdp(it), it.name) }
        listOf(EConfigType.VLESS, EConfigType.VMESS, EConfigType.TROJAN, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN, null)
            .forEach { assertFalse(AutoPing.carriesUdp(it), it?.name ?: "null") }
        assertTrue(AutoPing.carriesUdp(EConfigType.CUSTOM, custom("hysteria", "freedom", "dns", "blackhole")))
        assertTrue(AutoPing.carriesUdp(EConfigType.CUSTOM, custom("Hysteria2")))
        assertFalse(AutoPing.carriesUdp(EConfigType.CUSTOM, custom("hysteria", "vless")))
        assertFalse(AutoPing.carriesUdp(EConfigType.CUSTOM, custom("freedom")))
        assertFalse(AutoPing.carriesUdp(EConfigType.CUSTOM, "not json"))
        assertFalse(AutoPing.carriesUdp(EConfigType.CUSTOM, null))

        assertEquals(AutoPing.Method.STUN, AutoPing.resolveMethod(AutoPing.Method.AUTO, carriesUdp = true))
        assertEquals(AutoPing.Method.HTTP, AutoPing.resolveMethod(AutoPing.Method.AUTO, carriesUdp = false))
        assertEquals(AutoPing.Method.STUN, AutoPing.resolveMethod(AutoPing.Method.STUN, carriesUdp = false))
        assertEquals(AutoPing.Method.HTTP, AutoPing.resolveMethod(AutoPing.Method.HTTP, carriesUdp = true))
    }
}

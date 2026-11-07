package com.amier.tether

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyFinderTest {

    @Test
    fun userPortIsTriedFirstWithoutDuplicates() {
        assertEquals(listOf(7000) + ProxyFinder.COMMON_PORTS, ProxyFinder.candidatePorts(7000))
        assertEquals(1080, ProxyFinder.candidatePorts(1080).first())
        assertEquals(ProxyFinder.COMMON_PORTS.size, ProxyFinder.candidatePorts(1080).size)
        assertEquals(9050, ProxyFinder.candidatePorts(null).first())
        assertEquals(ProxyFinder.COMMON_PORTS, ProxyFinder.candidatePorts(70000))
    }

    @Test
    fun portParsing() {
        assertEquals(9050, ProxyFinder.parsePort(" 9050 "))
        assertNull(ProxyFinder.parsePort(""))
        assertNull(ProxyFinder.parsePort("0"))
        assertNull(ProxyFinder.parsePort("65536"))
        assertNull(ProxyFinder.parsePort(null))
    }

    @Test
    fun socks5NoAuthReply() {
        assertTrue(ProxyFinder.isSocks5NoAuthReply(byteArrayOf(5, 0), 2))
        assertFalse(ProxyFinder.isSocks5NoAuthReply(byteArrayOf(5, 2), 2)) // wants a password
        assertFalse(ProxyFinder.isSocks5NoAuthReply(byteArrayOf(72, 84), 2)) // "HT…": an HTTP proxy
        assertFalse(ProxyFinder.isSocks5NoAuthReply(byteArrayOf(5, 0), 1))
    }

    @Test
    fun hevConfigPointsAtTheProxyAndMapsDns() {
        val config = ProxyFinder.hevConfig(ProxyTarget("192.168.43.1", 9050, ProxySource.GATEWAY))
        assertTrue(config.contains("  address: '192.168.43.1'\n"))
        assertTrue(config.contains("  port: 9050\n"))
        assertTrue(config.contains("  udp-address: '192.168.43.1'\n"))
        assertTrue(config.contains("mapdns:\n  address: ${ProxyFinder.MAPDNS_ADDRESS}\n"))
        assertTrue(config.contains("  ipv4: ${ProxyFinder.TUN_IPV4}\n"))
    }

    @Test
    fun formatting() {
        assertEquals("01:01:01", formatDuration(3_661_000))
        assertEquals("00:00:00", formatDuration(-5))
        assertEquals("512 B", formatBytes(512))
        assertTrue(formatBytes(5L * 1048576).endsWith("MB"))
        assertTrue(formatBytes(3L * 1073741824).endsWith("GB"))
    }
}

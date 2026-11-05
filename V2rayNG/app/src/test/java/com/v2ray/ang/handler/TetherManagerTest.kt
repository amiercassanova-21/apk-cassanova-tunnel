package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TetherManagerTest {

    @Test
    fun hotspotInterfaceWinsOverWifiClientAndMobileData() {
        val nifs = listOf(
            "rmnet_data0" to "10.239.184.77",
            "wlan0" to "192.168.1.20",
            "ap0" to "192.168.43.1",
            "tun0" to "10.10.14.1",
        )
        assertEquals("192.168.43.1", TetherManager.pickAddress(nifs))
    }

    @Test
    fun wifiClientOnlyWhenNoHotspot() {
        assertEquals("192.168.1.20", TetherManager.pickAddress(listOf("wlan0" to "192.168.1.20", "ccmni1" to "10.0.0.5")))
        assertEquals("192.168.42.129", TetherManager.pickAddress(listOf("rndis0" to "192.168.42.129", "wlan0" to "192.168.1.20")))
    }

    @Test
    fun noPrivateTetherAddressGivesNull() {
        assertNull(TetherManager.pickAddress(listOf("rmnet0" to "10.1.2.3", "ap0" to "100.64.0.1")))
        assertNull(TetherManager.pickAddress(emptyList()))
    }

    @Test
    fun privateRanges() {
        assertTrue(TetherManager.isPrivate("10.0.0.1"))
        assertTrue(TetherManager.isPrivate("172.20.1.1"))
        assertTrue(TetherManager.isPrivate("192.168.43.1"))
        assertFalse(TetherManager.isPrivate("172.32.0.1"))
        assertFalse(TetherManager.isPrivate("8.8.8.8"))
        assertFalse(TetherManager.isPrivate("x"))
    }

    @Test
    fun portsAndGuide() {
        assertEquals(9050, TetherManager.parsePort(null, 9050))
        assertEquals(9050, TetherManager.parsePort("70000", 9050))
        assertEquals(1080, TetherManager.parsePort(" 1080 ", 9050))
        assertEquals(
            listOf("SOCKS5: 192.168.43.1:9050", "HTTP: 192.168.43.1:9090", "socks://192.168.43.1:9050"),
            TetherManager.guide("192.168.43.1", 9050, 9090)
        )
    }
}

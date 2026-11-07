package com.v2ray.ang.handler

import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditionTest {

    @Test
    fun liteKeepsOnlyItsProtocols() {
        listOf(EConfigType.VLESS, EConfigType.VMESS, EConfigType.TROJAN, EConfigType.HYSTERIA2, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN, EConfigType.CUSTOM)
            .forEach { assertTrue(Edition.supports(it, lite = true), it.name) }
        listOf(EConfigType.SSH, EConfigType.SHADOWSOCKS, EConfigType.SOCKS, EConfigType.HTTP, EConfigType.WIREGUARD, EConfigType.HYSTERIA)
            .forEach { assertFalse(Edition.supports(it, lite = true), it.name) }
    }

    @Test
    fun fullEditionSupportsEverything() {
        EConfigType.entries.forEach { assertTrue(Edition.supports(it, lite = false), it.name) }
    }

    private fun custom(vararg protocols: String): String =
        """{"inbounds":[],"routing":{},"outbounds":[${protocols.joinToString(",") { """{"tag":"t","protocol":"$it"}""" }}]}"""

    @Test
    fun liteAcceptsCustomWithSupportedOutbounds() {
        val raw = custom("vless", "vmess", "trojan", "hysteria", "freedom", "blackhole", "dns", "loopback")
        assertNull(Edition.unsupportedCustomProtocol(raw))
        assertTrue(Edition.supportsCustom(raw, lite = true))
        assertNull(Edition.unsupportedCustomProtocol(custom("Hysteria2", " VLESS ")))
    }

    @Test
    fun liteRejectsCustomWithOtherOutbounds() {
        val raw = custom("vless", "shadowsocks", "socks")
        assertEquals("shadowsocks", Edition.unsupportedCustomProtocol(raw))
        assertFalse(Edition.supportsCustom(raw, lite = true))
        assertTrue(Edition.supportsCustom(raw, lite = false))
        assertEquals("wireguard", Edition.unsupportedCustomProtocol(custom("wireguard")))
    }

    @Test
    fun customWithoutReadableOutboundsIsLeftToTheParser() {
        assertNull(Edition.unsupportedCustomProtocol(null))
        assertNull(Edition.unsupportedCustomProtocol(""))
        assertNull(Edition.unsupportedCustomProtocol("not json {"))
        assertNull(Edition.unsupportedCustomProtocol("[1,2]"))
        assertNull(Edition.unsupportedCustomProtocol("""{"outbounds":{}}"""))
        assertNull(Edition.unsupportedCustomProtocol("""{"outbounds":[1,{"tag":"x"}]}"""))
    }
}

package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigLabelsTest {

    private fun ssh(mode: String?) = ProfileItem.create(EConfigType.SSH).apply { sshMode = mode }

    @Test
    fun protocolFamilyOfEachConfigType() {
        assertEquals(ProtocolFilter.SLOWDNS, ProtocolFilter.of(ssh("slowdns")))
        assertEquals(ProtocolFilter.SSH, ProtocolFilter.of(ssh("payload")))
        assertEquals(ProtocolFilter.SSH, ProtocolFilter.of(ssh(null)))
        assertEquals(ProtocolFilter.HY2, ProtocolFilter.of(ProfileItem.create(EConfigType.HYSTERIA2)))
        assertEquals(ProtocolFilter.HY2, ProtocolFilter.of(ProfileItem.create(EConfigType.HYSTERIA)))
        assertEquals(ProtocolFilter.XRAY, ProtocolFilter.of(ProfileItem.create(EConfigType.VLESS)))
        assertEquals(ProtocolFilter.XRAY, ProtocolFilter.of(ProfileItem.create(EConfigType.TROJAN)))
    }

    @Test
    fun filterAllMatchesEverythingAndOthersOnlyTheirFamily() {
        val slow = ssh("slowdns")
        assertTrue(ProtocolFilter.ALL.matches(slow))
        assertTrue(ProtocolFilter.SLOWDNS.matches(slow))
        assertFalse(ProtocolFilter.SSH.matches(slow))
        assertFalse(ProtocolFilter.XRAY.matches(slow))
    }

    @Test
    fun maskHidesTheLastPartOfTheHost() {
        assertEquals("sgdo9.cassanova.***", maskHost("sgdo9.cassanova.biz"))
        assertEquals("10.1.2.***", maskHost("10.1.2.3"))
        assertEquals("2001:db8:***", maskHost("[2001:db8::1]"))
        assertEquals("loc***", maskHost("localhost"))
        assertEquals("", maskHost(null))
        assertEquals("", maskHost("  "))
    }

    @Test
    fun maskedAddressAddsThePortWhenPresent() {
        assertEquals("a.*** : 53", maskedAddress("a.b", "53"))
        assertEquals("a.***", maskedAddress("a.b", ""))
        assertEquals("", maskedAddress("", "53"))
    }

    @Test
    fun transportPartsSkipDefaultsAndNonXray() {
        val vless = ProfileItem.create(EConfigType.VLESS).apply { network = "ws"; security = "tls" }
        assertEquals(listOf("ws", "tls"), transportParts(vless))
        val plain = ProfileItem.create(EConfigType.VMESS).apply { network = "tcp"; security = "" }
        assertEquals(emptyList<String>(), transportParts(plain))
        assertEquals(emptyList<String>(), transportParts(ssh("ssl")))
    }

    @Test
    fun pingLevelBoundaries() {
        assertEquals(PingLevel.NONE, pingLevel(0L))
        assertEquals(PingLevel.FAILED, pingLevel(-1L))
        assertEquals(PingLevel.GOOD, pingLevel(200L))
        assertEquals(PingLevel.SLOW, pingLevel(201L))
    }

    @Test
    fun badgeNamesForCommonTypes() {
        assertEquals("SSH", protocolBadge(ssh("direct")))
        assertEquals("VL", protocolBadge(ProfileItem.create(EConfigType.VLESS)))
        assertEquals("HY2", protocolBadge(ProfileItem.create(EConfigType.HYSTERIA2)))
    }
}

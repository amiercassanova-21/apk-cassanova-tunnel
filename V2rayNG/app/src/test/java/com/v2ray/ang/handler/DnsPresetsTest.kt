package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsPresetsTest {

    @Test
    fun appDefaultIsCloudflare() {
        assertEquals(DnsPresets.DEFAULT_ID, DnsPresets.detect(AppConfig.DNS_VPN, AppConfig.DNS_PROXY)?.id)
        // Blank remote DNS falls back to the app default in the core.
        assertEquals("cloudflare", DnsPresets.detect("1.1.1.1", "")?.id)
        assertEquals("cloudflare", DnsPresets.detect("1.1.1.1", null)?.id)
    }

    @Test
    fun everyPresetIsReadBackFromTheValuesItWrites() {
        DnsPresets.ALL.forEach { p ->
            assertEquals(p.id, DnsPresets.detect(p.vpnDnsValue, p.remoteDns)?.id)
        }
        assertEquals(DnsPresets.ALL.size, DnsPresets.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun toleratesSpacesSlashAndCase() {
        assertEquals("adguard", DnsPresets.detect(" 94.140.14.14 , 94.140.15.15 ", "HTTPS://dns.adguard-dns.com/dns-query/ ")?.id)
        assertEquals("google", DnsPresets.detect("8.8.4.4", "https://dns.google/dns-query")?.id)
    }

    @Test
    fun mixedOrEditedValuesAreCustom() {
        // AdGuard remote but Cloudflare VPN DNS: phone DNS would not block ads.
        assertNull(DnsPresets.detect("1.1.1.1", "https://dns.adguard-dns.com/dns-query"))
        assertNull(DnsPresets.detect("94.140.14.14,1.1.1.1", "https://dns.adguard-dns.com/dns-query"))
        assertNull(DnsPresets.detect("1.1.1.1", "https://cloudflare-dns.com/dns-query,8.8.8.8"))
        assertNull(DnsPresets.detect("1.1.1.1", "1.1.1.1"))
        assertNull(DnsPresets.detect("", AppConfig.DNS_PROXY))
        assertNull(DnsPresets.detect(null, AppConfig.DNS_PROXY))
    }

    @Test
    fun customDohUrlTakesFirstHttpsEntry() {
        assertEquals("https://dns.nextdns.io/abc123", DnsPresets.customDohUrl("8.8.8.8, https://dns.nextdns.io/abc123,https://x/y"))
        assertNull(DnsPresets.customDohUrl("8.8.8.8,tcp://1.1.1.1"))
        assertNull(DnsPresets.customDohUrl(null))
    }

    @Test
    fun hostOfHidesPathAndCredentials() {
        assertEquals("dns.nextdns.io", DnsPresets.hostOf("https://dns.nextdns.io/abc123"))
        assertEquals("freedns.controld.com", DnsPresets.hostOf("https://freedns.controld.com/p2"))
        assertEquals("host.example", DnsPresets.hostOf("https://user:secret@host.example/q?x=1"))
        assertEquals("8.8.8.8", DnsPresets.hostOf("8.8.8.8,1.1.1.1"))
        assertNull(DnsPresets.hostOf(""))
        assertNull(DnsPresets.hostOf(null))
    }

    @Test
    fun presetsUseDohAndPlainIpv4() {
        val ipv4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
        DnsPresets.ALL.forEach { p ->
            assertTrue(p.remoteDns.startsWith("https://"), p.id)
            assertTrue(p.vpnDns.isNotEmpty() && p.vpnDns.all { ipv4.matches(it) }, p.id)
        }
    }
}

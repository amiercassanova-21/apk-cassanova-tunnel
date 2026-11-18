package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsRulesTest {

    @Test
    fun parsesTypedDomains() {
        assertEquals(
            listOf("domain:shopee.co.id", "domain:ads.example.com", "domain:tracker.net"),
            DnsRules.parseDomains(" Shopee.co.id, ads.example.com\ntracker.net ;shopee.co.id ")
        )
    }

    @Test
    fun cutsLinksDownToTheHost() {
        assertEquals(
            listOf("domain:shopee.co.id", "domain:x.example.com"),
            DnsRules.parseDomains("https://shopee.co.id/product/123?ref=a, http://user@x.example.com:8080/")
        )
    }

    @Test
    fun keepsXrayPrefixes() {
        assertEquals(
            listOf("geosite:category-ads-all", "full:a.b.com", "regexp:^ad[0-9]+\\.", "keyword:track"),
            DnsRules.parseDomains("geosite:category-ads-all, full:a.b.com, regexp:^ad[0-9]+\\., keyword:track")
        )
    }

    @Test
    fun emptyInputGivesNoRules() {
        assertTrue(DnsRules.parseDomains(null).isEmpty())
        assertTrue(DnsRules.parseDomains(" ,\n ; ").isEmpty())
        assertTrue(DnsRules.parseDomains("https:///").isEmpty())
    }

    @Test
    fun adsRoutingOnlyForAdBlockingChoices() {
        assertTrue(DnsRules.adsRoutingBlock("94.140.14.14,94.140.15.15", "https://dns.adguard-dns.com/dns-query"))
        assertTrue(DnsRules.adsRoutingBlock("76.76.2.2", "https://freedns.controld.com/p2"))
        assertFalse(DnsRules.adsRoutingBlock("1.1.1.1", "https://cloudflare-dns.com/dns-query"))
        assertFalse(DnsRules.adsRoutingBlock("94.140.14.15", "https://family.adguard-dns.com/dns-query"))
        // Custom (mixed) values: no automatic ad rule.
        assertFalse(DnsRules.adsRoutingBlock("1.1.1.1", "https://dns.adguard-dns.com/dns-query"))
    }

    @Test
    fun ipv4OnlyWhenIpv6Off() {
        assertEquals("UseIPv4", DnsRules.queryStrategy(false))
        assertNull(DnsRules.queryStrategy(true))
    }

    @Test
    fun slowDnsBoostNeedsSwitchVpnAndSlowDns() {
        assertTrue(DnsRules.slowDnsBoost(switchOn = true, vpnMode = true, slowDnsProfile = true))
        assertFalse(DnsRules.slowDnsBoost(switchOn = false, vpnMode = true, slowDnsProfile = true))
        assertFalse(DnsRules.slowDnsBoost(switchOn = true, vpnMode = false, slowDnsProfile = true))
        assertFalse(DnsRules.slowDnsBoost(switchOn = true, vpnMode = true, slowDnsProfile = false))
    }
}

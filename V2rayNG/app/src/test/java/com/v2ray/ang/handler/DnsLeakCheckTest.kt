package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsLeakCheckTest {

    private val cloudflare = "AS13335 Cloudflare, Inc."
    private val telkomsel = "AS23693 PT. Telekomunikasi Selular"

    @Test
    fun readsAsnAndName() {
        assertEquals("AS13335", DnsLeakCheck.asn(cloudflare))
        assertEquals("Cloudflare, Inc.", DnsLeakCheck.orgName(cloudflare))
        assertNull(DnsLeakCheck.asn("Some ISP"))
        assertEquals("Some ISP", DnsLeakCheck.orgName("Some ISP"))
        assertNull(DnsLeakCheck.asn(null))
        assertNull(DnsLeakCheck.orgName(" "))
    }

    @Test
    fun sameNetworkAsThePhoneIsALeak() {
        assertEquals(DnsLeakCheck.Verdict.LEAK, DnsLeakCheck.verdict(telkomsel, "AS23693 Telkomsel"))
        assertEquals(DnsLeakCheck.Verdict.SAFE, DnsLeakCheck.verdict(cloudflare, telkomsel))
        assertEquals(DnsLeakCheck.Verdict.UNKNOWN, DnsLeakCheck.verdict(null, telkomsel))
        assertEquals(DnsLeakCheck.Verdict.UNKNOWN, DnsLeakCheck.verdict(cloudflare, null))
    }

    @Test
    fun parsesIpInfoReply() {
        val info = DnsLeakCheck.parseIpInfo(
            """{"ip":"172.71.1.2","city":"Singapore","region":"Singapore","country":"SG","org":"AS13335 Cloudflare, Inc."}"""
        )
        assertEquals(DnsLeakCheck.IpInfo("172.71.1.2", cloudflare, "SG", "Singapore"), info)
        assertNull(DnsLeakCheck.parseIpInfo("not json"))
        assertNull(DnsLeakCheck.parseIpInfo(null))
        // Private address: ipinfo gives no org.
        assertNull(DnsLeakCheck.parseIpInfo("""{"ip":"10.0.0.1","bogon":true}""")?.org)
    }

    @Test
    fun recognisesFakeDnsAddresses() {
        assertTrue(DnsLeakCheck.isFakeDnsIp("198.18.0.5"))
        assertTrue(DnsLeakCheck.isFakeDnsIp("198.19.255.1"))
        assertFalse(DnsLeakCheck.isFakeDnsIp("198.180.1.1"))
        assertFalse(DnsLeakCheck.isFakeDnsIp(null))
    }
}

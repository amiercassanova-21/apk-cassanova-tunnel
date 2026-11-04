package com.v2ray.ang.fmt

import com.google.gson.Gson
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SshFmtTest {

    private fun slowDnsProfile() = ProfileItem.create(EConfigType.SSH).apply {
        remarks = "DO9 SLOW DNS"
        server = "sgdo9.example.com"
        serverPort = "53"
        username = "admin09"
        password = "p@ss word"
        sshMode = "slowdns"
        sshPayload = "GET / HTTP/1.1[crlf]Host: [host][crlf][crlf]"
        sni = "bug.example.com"
        sshUdpgwPort = "7300"
        dnsResolver = "https://dns.google/dns-query"
        dnsNameserver = "ns.sgdo9.example.com"
        dnsPublicKey = "a".repeat(64)
        dnsRecordType = "null"
    }

    @Test
    fun roundTripKeepsEverySshField() {
        val original = slowDnsProfile()
        val json = Gson().toJson(SshFmt.toShareItem(original))
        val restored = SshFmt.fromShareItem(Gson().fromJson(json, SshFmt.SshShareItem::class.java))!!
        assertEquals(EConfigType.SSH, restored.configType)
        listOf<(ProfileItem) -> Any?>(
            { it.remarks }, { it.server }, { it.serverPort }, { it.username }, { it.password },
            { it.sshMode }, { it.sshPayload }, { it.sni }, { it.sshUdpgwPort }, { it.dnsResolver },
            { it.dnsNameserver }, { it.dnsPublicKey }, { it.dnsRecordType },
        ).forEach { field -> assertEquals(field(original), field(restored)) }
    }

    @Test
    fun linkFieldNamesAreStable() {
        val json = Gson().toJson(SshFmt.toShareItem(slowDnsProfile()))
        listOf("\"v\":1", "\"ps\":", "\"add\":", "\"port\":", "\"user\":", "\"pass\":", "\"mode\":",
            "\"payload\":", "\"sni\":", "\"udpgw\":", "\"dns\":", "\"ns\":", "\"key\":", "\"type\":")
            .forEach { assert(json.contains(it)) { "missing $it in $json" } }
    }

    @Test
    fun missingServerIsRejectedAndDefaultsApplied() {
        assertNull(SshFmt.fromShareItem(SshFmt.SshShareItem(add = "")))
        val p = SshFmt.fromShareItem(SshFmt.SshShareItem(add = "h.example.com"))!!
        assertEquals("22", p.serverPort)
        assertEquals("h.example.com", p.remarks)
    }
}

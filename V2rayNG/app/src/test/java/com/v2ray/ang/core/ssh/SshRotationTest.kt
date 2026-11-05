package com.v2ray.ang.core.ssh

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SshRotationTest {

    private fun settings(payload: String = "GET / HTTP/1.1[crlf][crlf]", sni: String = "", proxy: String = "") =
        SshSettings(host = "h", port = 80, username = "u", password = "p", mode = SshMode.SSL_PAYLOAD,
            payload = payload, sni = sni, proxyHost = proxy, proxyPort = 80)

    @Test
    fun singleValuesMeanNoRotation() {
        val s = settings(sni = "bug.example.com", proxy = "1.2.3.4")
        assertEquals(1, s.variantCount)
        assertEquals(s, s.variant(5))
    }

    @Test
    fun payloadBlocksSplitOnDashLine() {
        val s = settings(payload = "A[crlf]\n---\nB[crlf]\r\n --- \r\nC")
        assertEquals(listOf("A[crlf]", "B[crlf]", "C"), s.payloadOptions)
        assertEquals("B[crlf]", s.variant(1).payload)
        assertEquals("A[crlf]", s.variant(3).payload)
    }

    @Test
    fun listsAdvanceIndependently() {
        val s = settings(payload = "P1\n---\nP2\n---\nP3", sni = "s1.com, s2.com", proxy = "x.com")
        assertEquals(3, s.variantCount)
        assertEquals(listOf("P1" to "s1.com", "P2" to "s2.com", "P3" to "s1.com"),
            (0..2).map { s.variant(it).let { v -> v.payload to v.sni } })
        assertEquals("x.com", s.variant(2).proxyHost)
    }
}

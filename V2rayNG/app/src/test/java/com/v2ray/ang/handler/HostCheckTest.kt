package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HostCheckTest {

    @Test
    fun outcomeStates() {
        assertEquals(HostCheck.State.CLOSED, HostCheck.outcome(tcpOpen = false, code = null, tlsFailed = false))
        assertEquals(HostCheck.State.RESPONDED, HostCheck.outcome(tcpOpen = true, code = 403, tlsFailed = false))
        assertEquals(HostCheck.State.TLS_ERROR, HostCheck.outcome(tcpOpen = true, code = null, tlsFailed = true))
        assertEquals(HostCheck.State.OPEN_NO_HTTP, HostCheck.outcome(tcpOpen = true, code = null, tlsFailed = false))
        assertEquals(HostCheck.State.OPEN_NO_HTTP, HostCheck.outcome(tcpOpen = true, code = -1, tlsFailed = false))
    }

    @Test
    fun serverLabel() {
        assertEquals("cloudflare", HostCheck.serverLabel("  cloudflare "))
        assertNull(HostCheck.serverLabel(null))
        assertNull(HostCheck.serverLabel("   "))
        assertEquals(32, HostCheck.serverLabel("x".repeat(80))!!.length)
    }

    @Test
    fun summaryLine() {
        val result = HostCheck.Result(
            "a.com", "1.2.3.4",
            listOf(
                HostCheck.Probe(80, HostCheck.State.RESPONDED, 40, 301, "nginx"),
                HostCheck.Probe(443, HostCheck.State.TLS_ERROR, 50),
            )
        )
        assertEquals("a.com 1.2.3.4 | 80:301 | 443:tls_error", HostCheck.summary(result))
        assertEquals("b.com -", HostCheck.summary(HostCheck.Result("b.com", null, emptyList())))
    }
}

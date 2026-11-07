package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomConnInfoTest {

    @Test
    fun summarizesTheLocalSocksInboundAndOutbounds() {
        val raw = """
            {"inbounds":[
               {"tag":"lan","port":1080,"listen":"192.168.1.5","protocol":"socks"},
               {"tag":"socks-in","port":10808,"listen":"127.0.0.1","protocol":"socks","settings":{"udp":true,"auth":"noauth"}},
               {"tag":"http-in","port":10809,"protocol":"http"}],
             "outbounds":[{"protocol":"vless"},{"protocol":"VLESS"},{"protocol":"freedom"},{"protocol":"blackhole"},{"protocol":"dns"}],
             "routing":{"balancers":[{"tag":"a"}]}}
        """
        assertEquals(CustomConnInfo.Summary(10808, socksAuth = false, socksUdp = true, proxyOutbounds = 2, balancers = 1), CustomConnInfo.summarize(raw))
    }

    @Test
    fun passwordInboundAndMissingParts() {
        val withAuth = """{"inbounds":[{"port":"2000","protocol":"socks","settings":{"auth":"password"}}]}"""
        assertEquals(CustomConnInfo.Summary(2000, socksAuth = true, socksUdp = false, proxyOutbounds = 0, balancers = 0), CustomConnInfo.summarize(withAuth))
        assertEquals(CustomConnInfo.Summary(null, false, false, 0, 0), CustomConnInfo.summarize("{}"))
        assertNull(CustomConnInfo.summarize("not json"))
        assertNull(CustomConnInfo.summarize(null))
    }

    @Test
    fun trafficVerdict() {
        assertEquals(CustomConnInfo.Traffic.NONE, CustomConnInfo.traffic(emptyList()))
        assertEquals(CustomConnInfo.Traffic.NONE, CustomConnInfo.traffic(listOf(0L to 0L, 0L to 0L)))
        assertEquals(CustomConnInfo.Traffic.UP_ONLY, CustomConnInfo.traffic(listOf(0L to 0L, 0L to 120L)))
        assertEquals(CustomConnInfo.Traffic.OK, CustomConnInfo.traffic(listOf(0L to 50L, 900L to 50L)))
    }
}

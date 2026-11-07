package com.v2ray.ang.handler

import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditionTest {

    @Test
    fun liteKeepsOnlyItsProtocols() {
        listOf(EConfigType.VLESS, EConfigType.VMESS, EConfigType.TROJAN, EConfigType.HYSTERIA2, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)
            .forEach { assertTrue(Edition.supports(it, lite = true), it.name) }
        listOf(EConfigType.SSH, EConfigType.SHADOWSOCKS, EConfigType.SOCKS, EConfigType.HTTP, EConfigType.WIREGUARD, EConfigType.CUSTOM, EConfigType.HYSTERIA)
            .forEach { assertFalse(Edition.supports(it, lite = true), it.name) }
    }

    @Test
    fun fullEditionSupportsEverything() {
        EConfigType.entries.forEach { assertTrue(Edition.supports(it, lite = false), it.name) }
    }
}

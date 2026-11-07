package com.v2ray.ang.handler

import com.v2ray.ang.BuildConfig
import com.v2ray.ang.enums.EConfigType

/**
 * Cassanova: "Amier VPN Lite" (lite flavor) keeps only the Xray protocols VLESS, VMess, Trojan
 * and Hysteria2 (plus groups/chains built from them). SSH, SlowDNS and the other protocols are
 * hidden, rejected on import, and their native engines are not packaged.
 */
object Edition {
    val isLite: Boolean get() = BuildConfig.LITE

    private val liteTypes = setOf(
        EConfigType.VMESS,
        EConfigType.VLESS,
        EConfigType.TROJAN,
        EConfigType.HYSTERIA2,
        EConfigType.POLICYGROUP,
        EConfigType.PROXYCHAIN,
    )

    /** Whether this edition can add, import and connect [type]. */
    fun supports(type: EConfigType, lite: Boolean = isLite): Boolean = !lite || type in liteTypes
}

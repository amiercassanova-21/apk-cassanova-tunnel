package com.v2ray.ang.handler

import com.google.gson.JsonParser
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
        // Custom JSON is allowed only when its outbounds pass [unsupportedCustomProtocol].
        EConfigType.CUSTOM,
    )

    /**
     * Xray outbound protocols Lite can run inside a custom JSON config. Xray names the
     * Hysteria2 outbound `hysteria`; `hysteria2` is accepted as well.
     */
    private val liteCustomOutbounds = setOf(
        "vless", "vmess", "trojan", "hysteria", "hysteria2",
        "freedom", "blackhole", "dns", "loopback",
    )

    /** Whether this edition can add, import and connect [type]. */
    fun supports(type: EConfigType, lite: Boolean = isLite): Boolean = !lite || type in liteTypes

    /**
     * The first outbound protocol of the custom JSON [raw] that Lite cannot run, or null when
     * every outbound is supported. JSON that does not parse also returns null: the custom
     * config parser rejects it on its own.
     */
    fun unsupportedCustomProtocol(raw: String?): String? {
        val json = try {
            raw?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val outbounds = json.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        for (outbound in outbounds) {
            val protocol = outbound.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("protocol")?.takeIf { it.isJsonPrimitive }?.asString
                ?.trim()?.lowercase() ?: continue
            if (protocol !in liteCustomOutbounds) return protocol
        }
        return null
    }

    /** Whether this edition can import and connect the custom JSON config [raw]. */
    fun supportsCustom(raw: String?, lite: Boolean = isLite): Boolean =
        !lite || unsupportedCustomProtocol(raw) == null
}

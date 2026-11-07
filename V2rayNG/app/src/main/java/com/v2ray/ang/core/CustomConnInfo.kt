package com.v2ray.ang.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Cassanova: what the connection log says about a custom JSON config (pure, unit tested): its
 * local SOCKS inbound, how many proxy outbounds and balancers it has, and whether traffic moved
 * shortly after connecting.
 */
object CustomConnInfo {

    /** Delay after "core ready" before the traffic check. */
    const val TRAFFIC_CHECK_MS = 20_000L

    data class Summary(
        /** Port of the first SOCKS inbound on a loopback/any address, null when there is none. */
        val socksPort: Int?,
        /** True when that inbound asks for a password. */
        val socksAuth: Boolean,
        val socksUdp: Boolean,
        val proxyOutbounds: Int,
        val balancers: Int,
    )

    private val nonProxy = setOf("freedom", "blackhole", "dns", "loopback")
    private val localListen = setOf("", "127.0.0.1", "0.0.0.0", "::", "::1", "localhost")

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    /** Null when [raw] is not a JSON object. */
    fun summarize(raw: String?): Summary? {
        val json = try {
            raw?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val inbounds = json.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()
        val socks = inbounds.firstOrNull {
            it.str("protocol") == "socks" && (it.str("listen") ?: "").trim() in localListen
        }
        val settings = socks?.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject
        val outbounds = json.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()
        val balancers = json.get("routing")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("balancers")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
        return Summary(
            socksPort = socks?.str("port")?.toIntOrNull(),
            socksAuth = settings?.str("auth")?.lowercase() == "password",
            socksUdp = settings?.get("udp")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
            proxyOutbounds = outbounds.count { (it.str("protocol") ?: "").lowercase() !in nonProxy },
            balancers = balancers,
        )
    }

    enum class Traffic { NONE, UP_ONLY, OK }

    /** From the (down, up) speed samples since connecting. */
    fun traffic(history: List<Pair<Long, Long>>): Traffic = when {
        history.any { it.first > 0 } -> Traffic.OK
        history.any { it.second > 0 } -> Traffic.UP_ONLY
        else -> Traffic.NONE
    }
}

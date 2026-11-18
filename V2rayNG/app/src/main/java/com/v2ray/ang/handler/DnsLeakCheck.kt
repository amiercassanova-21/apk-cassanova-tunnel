package com.v2ray.ang.handler

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.SocksUdpClient
import com.v2ray.ang.util.LogUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Cassanova: in-app DNS leak check. The app is excluded from its own VPN, so it cannot look up
 * names like other apps do. Instead it sends a DNS query to the "VPN DNS" address through the
 * local SOCKS inbound, which is where Hev TUN delivers the DNS of every other app, so the query
 * takes their path (tunnel, routing, local DNS when on). The name asked is Akamai's
 * `whoami.akamai.net`, whose answer is the address of the resolver that asked Akamai. That
 * resolver's network is then compared with the network the phone uses without the VPN: the
 * same network means DNS goes to the operator (leak).
 *
 * It cannot see leaks outside that path (the phone's Private DNS, apps excluded by per-app
 * proxy); the browser test covers those.
 */
object DnsLeakCheck {

    const val WHOAMI_NAME = "whoami.akamai.net"
    const val FULL_TEST_URL = "https://browserleaks.com/dns"
    const val ADBLOCK_TEST_URL = "https://adblock.turtlecute.org/"
    private const val IP_INFO_URL = "https://ipinfo.io/"
    private const val TIMEOUT_MS = 8_000

    enum class Verdict { SAFE, LEAK, UNKNOWN }

    /** Network details from ipinfo.io; [org] looks like "AS13335 Cloudflare, Inc.". */
    data class IpInfo(val ip: String?, val org: String?, val country: String?, val city: String?)

    data class Result(
        val resolverIp: String?,
        val resolver: IpInfo?,
        val operator: IpInfo?,
        val verdict: Verdict,
    )

    /** "AS13335" from an ipinfo org string, null when there is none. */
    fun asn(org: String?): String? =
        org?.trim()?.substringBefore(' ')?.uppercase()?.takeIf { it.matches(Regex("^AS\\d+$")) }

    /** The org without its AS number: "Cloudflare, Inc.". */
    fun orgName(org: String?): String? =
        org?.trim()?.let { if (asn(it) != null) it.substringAfter(' ', "").trim() else it }?.takeIf { it.isNotEmpty() }

    /**
     * LEAK when the resolver is in the same network (AS) as the phone's own connection, SAFE when
     * both are known and differ, UNKNOWN otherwise.
     */
    fun verdict(resolverOrg: String?, operatorOrg: String?): Verdict {
        val r = asn(resolverOrg) ?: return Verdict.UNKNOWN
        val o = asn(operatorOrg) ?: return Verdict.UNKNOWN
        return if (r == o) Verdict.LEAK else Verdict.SAFE
    }

    /** Addresses from Xray's FakeDNS pool (198.18.0.0/15): the real resolver stays hidden. */
    fun isFakeDnsIp(ip: String?): Boolean = ip != null && (ip.startsWith("198.18.") || ip.startsWith("198.19."))

    /** Parses an ipinfo.io JSON reply; null when it is not one. */
    fun parseIpInfo(json: String?): IpInfo? = try {
        val o = json?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
        o?.let {
            fun f(name: String) = it.get(name)?.takeIf { e -> e.isJsonPrimitive }?.asString?.takeIf { s -> s.isNotBlank() }
            IpInfo(f("ip"), f("org"), f("country"), f("city"))
        }
    } catch (e: Exception) {
        null
    }

    /** The resolver address seen by Akamai, asked through the local SOCKS inbound; null on failure. */
    fun resolverIp(): String? {
        val vpnDns = SettingsManager.getVpnDnsServers().firstOrNull { !it.contains(':') } ?: AppConfig.DNS_VPN
        return try {
            SocksUdpClient(SettingsManager.getSocksPort(), TIMEOUT_MS).use { client ->
                DnsWire.parse(client.exchange(vpnDns, 53, DnsWire.query(WHOAMI_NAME)))?.ipv4?.firstOrNull()
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "DNS leak check: query through the local proxy failed", e)
            null
        }
    }

    private fun client(proxyPort: Int?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        if (proxyPort != null) LocalProxyClient.useProxy(builder, proxyPort)
        return builder.build()
    }

    private fun fetchInfo(client: OkHttpClient, url: String): IpInfo? = try {
        client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use {
            if (it.isSuccessful) parseIpInfo(it.body?.string()) else null
        }
    } catch (e: Exception) {
        LogUtil.w(AppConfig.TAG, "DNS leak check: network lookup failed", e)
        null
    }

    /**
     * Runs the whole check (blocking; call off the main thread). [proxyPort] is the local HTTP
     * proxy (the VPN is connected): the resolver lookup goes through it; the phone's own network
     * is looked up directly, which is what the app sees outside its VPN.
     */
    fun run(proxyPort: Int): Result {
        val ip = resolverIp()
        val viaProxy = client(proxyPort)
        val direct = client(null)
        try {
            if (isFakeDnsIp(ip)) return Result(ip, null, null, Verdict.UNKNOWN)
            val resolver = ip?.let { fetchInfo(viaProxy, "$IP_INFO_URL$it/json") }
            val operator = fetchInfo(direct, "${IP_INFO_URL}json")
            return Result(ip, resolver, operator, if (ip == null) Verdict.UNKNOWN else verdict(resolver?.org, operator?.org))
        } finally {
            for (c in listOf(viaProxy, direct)) {
                c.dispatcher.executorService.shutdown()
                c.connectionPool.evictAll()
            }
        }
    }
}

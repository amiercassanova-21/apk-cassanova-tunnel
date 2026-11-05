package com.v2ray.ang.handler

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * "Host to IP" tool: resolves hostnames through the phone's DNS (this app is outside the VPN, so
 * that is the operator/Wi-Fi DNS) or through Cloudflare / Google DNS-over-HTTPS.
 */
object HostLookup {
    const val MAX_HOSTS = 20
    const val HISTORY_SIZE = 10
    private const val TIMEOUT_MS = 6000
    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28
    private const val DOH_NXDOMAIN = 3

    enum class Source(val key: String) {
        SYSTEM("system"), CLOUDFLARE("cloudflare"), GOOGLE("google");

        companion object {
            fun fromKey(key: String?): Source = entries.firstOrNull { it.key == key } ?: SYSTEM
        }
    }

    enum class Failure { NOT_FOUND, NETWORK }

    data class Result(val host: String, val ips: List<String>, val millis: Long, val failure: Failure?)

    fun source(): Source = Source.fromKey(MmkvManager.decodeSettingsString(AppConfig.PREF_HOST_LOOKUP_SOURCE))

    fun saveSource(source: Source) {
        MmkvManager.encodeSettings(AppConfig.PREF_HOST_LOOKUP_SOURCE, source.key)
    }

    fun history(): List<String> =
        MmkvManager.decodeSettingsString(AppConfig.PREF_HOST_LOOKUP_HISTORY).orEmpty().lines().filter { it.isNotBlank() }

    fun saveHistory(history: List<String>) {
        MmkvManager.encodeSettings(AppConfig.PREF_HOST_LOOKUP_HISTORY, history.joinToString("\n"))
    }

    /** Blocking; call off the main thread. */
    fun lookup(host: String, source: Source): Result {
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000
        if (Utils.isPureIpAddress(host)) return Result(host, listOf(host), 0, null)
        return try {
            val ips = when (source) {
                Source.SYSTEM -> InetAddress.getAllByName(HttpUtil.toIdnDomain(host))
                    .sortedBy { it is Inet6Address }
                    .mapNotNull { it.hostAddress }
                    .distinct()
                else -> resolveDoh(HttpUtil.toIdnDomain(host), source)
            }
            Result(host, ips, elapsed(), if (ips.isEmpty()) Failure.NOT_FOUND else null)
        } catch (_: UnknownHostException) {
            Result(host, emptyList(), elapsed(), Failure.NOT_FOUND)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Host lookup failed, source=${source.key}", e)
            Result(host, emptyList(), elapsed(), Failure.NETWORK)
        }
    }

    private fun resolveDoh(host: String, source: Source): List<String> {
        var nxdomain = false
        val ips = listOf(TYPE_A, TYPE_AAAA).flatMap { type ->
            val body = HttpUtil.getUrlContentWithUserAgent(
                UrlContentRequest(
                    url = dohUrl(source, host, type),
                    timeout = TIMEOUT_MS,
                    requestHeaders = "{\"accept\":\"application/dns-json\"}",
                )
            )
            if (dohStatus(body) == DOH_NXDOMAIN) nxdomain = true
            parseDohAnswer(body, type)
        }
        if (ips.isEmpty() && nxdomain) throw UnknownHostException(host)
        return ips.distinct()
    }

    // ---------- pure helpers (unit tested) ----------

    /** Hosts from free text: one per line (commas and spaces also split), deduplicated, at most [MAX_HOSTS]. */
    fun parseHosts(text: String): List<String> =
        text.split('\n', ',', ' ', '\t', ';')
            .mapNotNull { normalizeHost(it) }
            .distinct()
            .take(MAX_HOSTS)

    /** "https://user@Sub.Example.com:443/path?q" → "sub.example.com"; null when nothing usable. */
    fun normalizeHost(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty() || s.startsWith("#")) return null
        s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@')
        s = if (s.startsWith("[")) {
            s.substringAfter('[').substringBefore(']')
        } else if (s.count { it == ':' } == 1) {
            s.substringBefore(':')
        } else {
            s
        }
        s = s.trimEnd('.').lowercase()
        if (s.isEmpty() || s.any { it.isWhitespace() || it == '"' || it == '<' || it == '>' }) return null
        return s
    }

    fun dohUrl(source: Source, host: String, type: Int): String {
        val name = URLEncoder.encode(host, "UTF-8")
        return when (source) {
            Source.GOOGLE -> "https://dns.google/resolve?name=$name&type=$type"
            else -> "https://cloudflare-dns.com/dns-query?name=$name&type=$type"
        }
    }

    fun dohStatus(json: String?): Int? = try {
        JsonParser.parseString(json.orEmpty()).asJsonObject.get("Status")?.asInt
    } catch (_: Exception) {
        null
    }

    /** Addresses of [type] from a DNS JSON answer; CNAME hops and other records are skipped. */
    fun parseDohAnswer(json: String?, type: Int): List<String> = try {
        val answer = JsonParser.parseString(json.orEmpty()).asJsonObject.getAsJsonArray("Answer")
        answer?.mapNotNull { element ->
            val record = element.asJsonObject
            if (record.get("type")?.asInt == type) record.get("data")?.asString?.trim() else null
        }.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    /** Latest search first, without duplicates, at most [HISTORY_SIZE]. */
    fun pushHistory(history: List<String>, hosts: List<String>): List<String> =
        (hosts + history).distinct().take(HISTORY_SIZE)
}

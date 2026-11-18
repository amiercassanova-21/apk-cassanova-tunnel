package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Cassanova: times DoH servers and checks whether they block ads. Blocking network calls:
 * run off the main thread. When the app's local proxy is listening (VPN connected), the
 * queries go through it, so the result reflects the active connection; otherwise they go
 * direct.
 */
object DnsSpeedTest {

    /** Answered by every resolver; the timed query. */
    const val PROBE_NAME = "www.google.com"

    /** A well-known ad domain; the first (warm-up) query and the ad-block check. */
    const val AD_NAME = "doubleclick.net"

    private const val TIMED_QUERIES = 3
    private const val TIMEOUT_MS = 6_000L
    private const val PORT_CHECK_MS = 300

    /** [millis] is the median of the timed queries, null when they all failed. */
    data class Measurement(val millis: Long?, val adsBlocked: Boolean?)

    /** The local HTTP proxy port when something listens on it, else null. */
    fun localProxyPort(): Int? {
        val port = SettingsManager.getHttpPort()
        if (port <= 0) return null
        return try {
            Socket().use { it.connect(InetSocketAddress(AppConfig.LOOPBACK, port), PORT_CHECK_MS) }
            port
        } catch (e: Exception) {
            null
        }
    }

    fun client(proxyPort: Int?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (proxyPort != null) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, proxyPort)))
            val user = SettingsManager.getSocksUsername()
            val pass = SettingsManager.getSocksPassword()
            if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                builder.proxyAuthenticator { _, response ->
                    if (response.request.header("Proxy-Authorization") != null) null
                    else response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(user, pass))
                        .build()
                }
            }
        }
        return builder.build()
    }

    private val dnsMessage = "application/dns-message".toMediaType()

    /** One DoH POST (RFC 8484); null on any failure. */
    private fun ask(client: OkHttpClient, url: String, name: String): DnsWire.Answer? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-message")
            .post(DnsWire.query(name).toRequestBody(dnsMessage))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()?.let { DnsWire.parse(it) }
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "DNS test: query to ${DnsPresets.hostOf(url)} failed", e)
            null
        }
    }

    /**
     * Measures [url]: a warm-up query for [AD_NAME] (opens the connection and gives the ad
     * verdict), then [TIMED_QUERIES] timed queries for [PROBE_NAME] on the same connection.
     */
    fun measure(client: OkHttpClient, url: String): Measurement {
        val ad = ask(client, url, AD_NAME)
        val times = ArrayList<Long>()
        repeat(TIMED_QUERIES) {
            val start = System.nanoTime()
            val answer = ask(client, url, PROBE_NAME)
            if (answer != null && answer.rcode == DnsWire.RCODE_NOERROR) {
                times.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
            }
        }
        return Measurement(median(times), DnsWire.isBlocked(ad))
    }

    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }
}

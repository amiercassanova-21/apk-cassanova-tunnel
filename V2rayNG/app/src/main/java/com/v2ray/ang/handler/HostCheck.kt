package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * "Host Checker": for each host, is port 80/443 open and what does the server answer over
 * HTTP/HTTPS (status code, Server header). Runs outside the VPN, i.e. over the operator network,
 * which is what matters when testing bug hosts.
 */
object HostCheck {
    private const val TIMEOUT_MS = 6000

    enum class State { RESPONDED, OPEN_NO_HTTP, TLS_ERROR, CLOSED, TIMEOUT }

    data class Probe(val port: Int, val state: State, val millis: Long, val code: Int? = null, val server: String? = null)

    data class Result(val host: String, val ip: String?, val probes: List<Probe>)

    /** Blocking; call off the main thread. */
    fun check(host: String): Result {
        val address = try {
            InetAddress.getAllByName(HttpUtil.toIdnDomain(host)).sortedBy { it is Inet6Address }.firstOrNull()
        } catch (_: UnknownHostException) {
            null
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Host check resolve failed", e)
            null
        } ?: return Result(host, null, emptyList())
        return Result(host, address.hostAddress, listOf(probe(host, address, 80), probe(host, address, 443)))
    }

    private fun probe(host: String, address: InetAddress, port: Int): Probe {
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000
        try {
            Socket().use { it.connect(InetSocketAddress(address, port), TIMEOUT_MS) }
        } catch (_: SocketTimeoutException) {
            return Probe(port, State.TIMEOUT, elapsed())
        } catch (_: Exception) {
            return Probe(port, State.CLOSED, elapsed())
        }
        val tcpMillis = elapsed()
        val scheme = if (port == 443) "https" else "http"
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$scheme://${HttpUtil.toIdnDomain(host)}/").openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("Connection", "close")
            }
            val code = connection.responseCode
            Probe(port, outcome(tcpOpen = true, code = code, tlsFailed = false), tcpMillis, code, serverLabel(connection.getHeaderField("Server")))
        } catch (_: SSLException) {
            Probe(port, outcome(tcpOpen = true, code = null, tlsFailed = true), tcpMillis)
        } catch (_: Exception) {
            Probe(port, outcome(tcpOpen = true, code = null, tlsFailed = false), tcpMillis)
        } finally {
            connection?.disconnect()
        }
    }

    // ---------- pure helpers (unit tested) ----------

    fun outcome(tcpOpen: Boolean, code: Int?, tlsFailed: Boolean): State = when {
        !tcpOpen -> State.CLOSED
        code != null && code > 0 -> State.RESPONDED
        tlsFailed -> State.TLS_ERROR
        else -> State.OPEN_NO_HTTP
    }

    /** "cloudflare", "nginx/1.25.3" … trimmed to a short label; null when absent. */
    fun serverLabel(header: String?): String? = header?.trim()?.takeIf { it.isNotEmpty() }?.take(32)

    /** One line per host for "Copy all". */
    fun summary(result: Result): String = buildString {
        append(result.host).append(' ').append(result.ip ?: "-")
        result.probes.forEach { p ->
            append(" | ").append(p.port).append(':')
            append(if (p.state == State.RESPONDED) p.code.toString() else p.state.name.lowercase())
        }
    }
}

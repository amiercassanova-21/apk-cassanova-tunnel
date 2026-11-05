package com.v2ray.ang.core.ssh

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.ConfigLockManager
import com.v2ray.ang.util.LogUtil
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Semaphore

/**
 * Real delay of an SSH profile for "Test all": opens a temporary SSH tunnel of that profile on a
 * free local port, then times one HTTP request through it, like Xray's real-delay test does for
 * other protocols. Xray cannot test SSH itself because SSH runs outside the core.
 *
 * Runs in the :tasks process (CoreTestService), whose package is outside the VPN, so the probe
 * reaches the server directly.
 */
object SshProbe {
    private const val TAG = AppConfig.TAG

    // Each probe starts an SSH session (and dnstt for SlowDNS); limit how many run at once.
    private val slots = Semaphore(MAX_PARALLEL)

    /** Delay in ms, or -1 when the tunnel or the request failed. */
    fun measure(context: Context, profile: ProfileItem, testUrl: String): Long {
        val settings = SshTunnelManager.toSettings(ConfigLockManager.unlock(profile))
        if (settings.validate() != null) return FAILED
        val connectTimeout = if (settings.mode == SshMode.SLOWDNS) SLOWDNS_CONNECT_MS else CONNECT_MS

        slots.acquire()
        var tunnel: SshEngine? = null
        return try {
            val port = SshTunnel.freePort()
            val t = SshTunnelManager.createEngine(context, settings, port, logger = { /* probes stay out of the log */ })
            tunnel = t
            t.start()
            if (!awaitConnected(t, connectTimeout)) {
                LogUtil.i(TAG, "SSH test: no session, mode=${settings.mode.key}")
                FAILED
            } else {
                timeRequest(port, testUrl)
            }
        } catch (e: Exception) {
            LogUtil.i(TAG, "SSH test failed, mode=${settings.mode.key}: ${e.javaClass.simpleName}")
            FAILED
        } finally {
            try {
                tunnel?.stop()
            } catch (_: Exception) {
            }
            slots.release()
        }
    }

    private fun awaitConnected(tunnel: SshEngine, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (tunnel.isConnected()) return true
            Thread.sleep(POLL_MS)
        }
        return tunnel.isConnected()
    }

    private fun timeRequest(port: Int, testUrl: String): Long {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        val start = System.currentTimeMillis()
        val conn = URL(testUrl).openConnection(proxy) as HttpURLConnection
        return try {
            conn.connectTimeout = REQUEST_MS
            conn.readTimeout = REQUEST_MS
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            val code = conn.responseCode
            if (code in 200..399) System.currentTimeMillis() - start else FAILED
        } finally {
            conn.disconnect()
        }
    }

    const val FAILED = -1L
    private const val MAX_PARALLEL = 3
    private const val CONNECT_MS = 15_000L
    private const val SLOWDNS_CONNECT_MS = 25_000L
    private const val REQUEST_MS = 10_000
    private const val POLL_MS = 200L
}

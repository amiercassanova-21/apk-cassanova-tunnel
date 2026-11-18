package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/**
 * Cassanova: lets the app's own tests (DNS test, internet speed test) go through the active
 * connection. The app is excluded from its own VPN, so its traffic only takes the tunnel
 * through the local HTTP proxy of the core.
 */
object LocalProxyClient {

    private const val PORT_CHECK_MS = 300

    /** The local HTTP proxy port when something listens on it (connected), else null. */
    fun port(): Int? {
        val port = SettingsManager.getHttpPort()
        if (port <= 0) return null
        return try {
            Socket().use { it.connect(InetSocketAddress(AppConfig.LOOPBACK, port), PORT_CHECK_MS) }
            port
        } catch (e: Exception) {
            null
        }
    }

    /** Sends [builder]'s requests through the local proxy at [port] (with its login, if set). */
    fun useProxy(builder: OkHttpClient.Builder, port: Int): OkHttpClient.Builder {
        builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, port)))
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
        return builder
    }
}

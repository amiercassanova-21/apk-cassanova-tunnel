package com.v2ray.ang.handler

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Proxy Tethering: while connected, Xray also listens on all interfaces at fixed ports (SOCKS5
 * 9050, HTTP 9090 by default) so devices on this phone's Wi-Fi hotspot can use the tunnel by
 * setting a proxy. Without login, as chosen: anyone on the hotspot can use it.
 *
 * The hotspot address is set by Android, not by the app, so it is detected and shown.
 */
object TetherManager {
    const val DEFAULT_SOCKS_PORT = 9050
    const val DEFAULT_HTTP_PORT = 9090

    fun isEnabled(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_TETHER_ENABLED, false)

    fun socksPort(): Int = parsePort(MmkvManager.decodeSettingsString(AppConfig.PREF_TETHER_SOCKS_PORT), DEFAULT_SOCKS_PORT)

    fun httpPort(): Int = parsePort(MmkvManager.decodeSettingsString(AppConfig.PREF_TETHER_HTTP_PORT), DEFAULT_HTTP_PORT)

    const val CUSTOM_SOCKS_TAG = "tether-socks"
    const val CUSTOM_HTTP_TAG = "tether-http"

    /**
     * Adds the tethering inbounds (SOCKS5 [socksPort] and HTTP [httpPort] on all interfaces, no
     * login) to a custom JSON config, copying the sniffing of its first SOCKS inbound. An inbound
     * whose tag or port the config already uses is skipped, so the user's own inbounds win.
     * Returns the ports added.
     */
    fun addCustomInbounds(json: JsonObject, socksPort: Int, httpPort: Int): List<Int> {
        val inbounds = json.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: JsonArray().also { json.add("inbounds", it) }
        val existing = inbounds.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
        val usedTags = existing.mapNotNull { it.get("tag")?.takeIf { t -> t.isJsonPrimitive }?.asString }.toSet()
        val usedPorts = existing.mapNotNull { it.get("port")?.takeIf { p -> p.isJsonPrimitive }?.asString?.toIntOrNull() }.toSet()
        val sniffing = existing.firstOrNull { it.get("protocol")?.takeIf { p -> p.isJsonPrimitive }?.asString == "socks" }
            ?.get("sniffing")?.takeIf { it.isJsonObject }?.deepCopy()
            ?: JsonObject().apply {
                addProperty("enabled", true)
                add("destOverride", JsonArray().apply { add("http"); add("tls") })
            }
        val added = mutableListOf<Int>()
        fun add(tag: String, protocol: String, port: Int, settings: JsonObject) {
            if (tag in usedTags || port in usedPorts) return
            inbounds.add(JsonObject().apply {
                addProperty("tag", tag)
                addProperty("protocol", protocol)
                addProperty("listen", "0.0.0.0")
                addProperty("port", port)
                add("settings", settings)
                add("sniffing", sniffing.deepCopy())
            })
            added.add(port)
        }
        add(CUSTOM_SOCKS_TAG, "socks", socksPort, JsonObject().apply { addProperty("auth", "noauth"); addProperty("udp", true) })
        add(CUSTOM_HTTP_TAG, "http", httpPort, JsonObject())
        return added
    }

    /** The phone's address on its hotspot (or USB/Bluetooth tethering) network, if any. */
    fun detectAddress(): String? = try {
        val candidates = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif ->
                nif.inetAddresses.toList().filterIsInstance<Inet4Address>().map { nif.name to (it.hostAddress ?: "") }
            }
        pickAddress(candidates)
    } catch (_: Exception) {
        null
    }

    /** NSD (mDNS) service type that Amier Tether looks for on the hotspot network. */
    const val NSD_SERVICE_TYPE = "_amiertether._tcp."

    private var nsdListener: NsdManager.RegistrationListener? = null

    /**
     * Daemon: announces the proxy on the local network so Amier Tether on the other phone finds
     * its address and port by itself. No-op when tethering is off or already announced.
     */
    @Synchronized
    fun advertise(context: Context) {
        if (!isEnabled() || nsdListener != null) return
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "Amier VPN"
            serviceType = NSD_SERVICE_TYPE
            port = socksPort()
            setAttribute("http", httpPort().toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                LogUtil.w(AppConfig.TAG, "Tether NSD: registration failed ($errorCode)")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdListener = listener
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Tether NSD: registerService failed", e)
        }
    }

    /** Daemon: stops the announcement when the connection stops. */
    @Synchronized
    fun stopAdvertising(context: Context) {
        val listener = nsdListener ?: return
        nsdListener = null
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        try {
            nsd.unregisterService(listener)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Tether NSD: unregisterService failed", e)
        }
    }

    // ---------- pure helpers (unit tested) ----------

    fun parsePort(value: String?, default: Int): Int =
        value?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: default

    private val tetherNames = listOf("ap", "swlan", "softap", "wlan", "rndis", "usb", "bt-pan", "ncm")
    private val upstreamNames = listOf("rmnet", "ccmni", "pdp", "seth", "v4-", "tun", "dummy", "lo")

    /**
     * Picks the hotspot address from (interface, IPv4) pairs: a private address on a tethering
     * interface (ap0, swlan0, wlan1, rndis0…), never the mobile-data or VPN interface. A Wi-Fi
     * client address on wlan0 only counts when no hotspot interface exists.
     */
    fun pickAddress(candidates: List<Pair<String, String>>): String? {
        val usable = candidates.filter { (name, ip) ->
            ip.isNotEmpty() && isPrivate(ip) && upstreamNames.none { name.startsWith(it) }
        }
        fun rank(name: String): Int = when {
            name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 0
            name.startsWith("wlan") && name != "wlan0" -> 1
            name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("ncm") -> 2
            name.startsWith("bt-pan") -> 3
            name == "wlan0" -> 4
            tetherNames.any { name.startsWith(it) } -> 5
            else -> 6
        }
        return usable.minByOrNull { rank(it.first) }?.second
    }

    fun isPrivate(ip: String): Boolean {
        val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }

    /** Proxy settings to type on the other device, plus a link apps like NetMod can import. */
    fun guide(ip: String, socksPort: Int, httpPort: Int): List<String> = listOf(
        "SOCKS5: $ip:$socksPort",
        "HTTP: $ip:$httpPort",
        "socks://$ip:$socksPort",
    )
}

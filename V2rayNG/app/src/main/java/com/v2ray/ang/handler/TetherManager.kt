package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
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

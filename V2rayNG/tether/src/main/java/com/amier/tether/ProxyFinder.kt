package com.amier.tether

import java.net.InetSocketAddress
import java.net.Socket

/** Where a proxy address came from, shown to the user. */
enum class ProxySource { AMIER_VPN, GATEWAY, MANUAL }

data class ProxyTarget(val host: String, val port: Int, val source: ProxySource)

/**
 * Finding the SOCKS5 proxy of the hotspot phone. On a hotspot the sharing phone is the Wi-Fi
 * gateway, so its address is known; the port is announced by Amier VPN (NSD) or found by trying
 * the ports tunnel apps commonly share on.
 */
object ProxyFinder {
    /** NSD service type that Amier VPN registers while Proxy Tethering is on. */
    const val NSD_SERVICE_TYPE = "_amiertether._tcp."

    /** Amier VPN's default first, then ports other tunnel apps (HTTP Custom, SSC, v2rayNG…) use. */
    val COMMON_PORTS = listOf(9050, 1080, 10808, 7071, 1088, 8080)

    /** Ports to try on the gateway: the user's own port first, without duplicates. */
    fun candidatePorts(userPort: Int?): List<Int> =
        (listOfNotNull(userPort?.takeIf { it in 1..65535 }) + COMMON_PORTS).distinct()

    fun parsePort(text: String?): Int? = text?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }

    /** A SOCKS5 server accepting "no authentication" answers 05 00 to the greeting 05 01 00. */
    fun isSocks5NoAuthReply(reply: ByteArray, length: Int): Boolean =
        length >= 2 && reply[0] == 0x05.toByte() && reply[1] == 0x00.toByte()

    /** Blocking SOCKS5 handshake check; call off the main thread. */
    fun probe(host: String, port: Int, timeoutMs: Int = 1500): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00))
            val reply = ByteArray(2)
            var read = 0
            while (read < 2) {
                val n = socket.getInputStream().read(reply, read, 2 - read)
                if (n < 0) break
                read += n
            }
            isSocks5NoAuthReply(reply, read)
        }
    } catch (_: Exception) {
        false
    }

    /** hev-socks5-tunnel configuration: everything into the proxy, DNS answered via mapped names. */
    fun hevConfig(target: ProxyTarget, mtu: Int = TUN_MTU): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: $mtu")
        appendLine("  ipv4: $TUN_IPV4")
        appendLine("  ipv6: '$TUN_IPV6'")
        appendLine("socks5:")
        appendLine("  port: ${target.port}")
        appendLine("  address: '${target.host}'")
        appendLine("  udp: 'udp'")
        // Some servers announce 0.0.0.0 or 127.0.0.1 for UDP; the relay is on the same host.
        appendLine("  udp-address: '${target.host}'")
        // DNS on the VPN is answered with mapped addresses, and connections to them are sent to
        // the proxy by name, so names are resolved on the sharing phone (works without UDP too).
        appendLine("mapdns:")
        appendLine("  address: $MAPDNS_ADDRESS")
        appendLine("  port: 53")
        appendLine("  network: 100.64.0.0")
        appendLine("  netmask: 255.192.0.0")
        appendLine("  cache-size: 10000")
        appendLine("misc:")
        appendLine("  tcp-read-write-timeout: 300000")
        appendLine("  udp-read-write-timeout: 60000")
        appendLine("  log-level: warn")
    }

    const val TUN_MTU = 1500
    const val TUN_IPV4 = "10.10.14.1"
    const val TUN_IPV6 = "fc00::10:10:14:1"
    const val MAPDNS_ADDRESS = "198.18.0.2"
}

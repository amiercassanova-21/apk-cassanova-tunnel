package com.v2ray.ang.core

import com.v2ray.ang.enums.EConfigType
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/**
 * Auto Ping settings and the pure parts of it (unit tested): modes, STUN packets, SOCKS5 UDP
 * framing and the failure policy. The runner is [AutoPingManager].
 */
object AutoPing {
    enum class Mode(val key: String) {
        OFF("off"), KEEPALIVE("keepalive"), RESET("reset"), AGGRESSIVE("aggressive");

        companion object {
            fun from(key: String?): Mode = entries.firstOrNull { it.key == key } ?: RESET
        }
    }

    enum class Method(val key: String) {
        /** Picked per connected config by [resolveMethod]. */
        AUTO("auto"), STUN("stun"), HTTP("http");

        companion object {
            fun from(key: String?): Method = entries.firstOrNull { it.key == key } ?: AUTO
        }
    }

    /** Custom JSON outbounds that are not proxies and say nothing about the link. */
    private val nonProxyOutbounds = setOf("freedom", "blackhole", "dns", "loopback")

    /** Proxy outbound protocols of the custom JSON [raw] (lowercase); empty if it does not parse. */
    fun customProxyProtocols(raw: String?): List<String> {
        val json = try {
            raw?.let { com.google.gson.JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val outbounds = json.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return outbounds.mapNotNull { outbound ->
            outbound.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("protocol")?.takeIf { it.isJsonPrimitive }?.asString
                ?.trim()?.lowercase()
                ?.takeIf { it.isNotEmpty() && it !in nonProxyOutbounds }
        }
    }

    /**
     * Whether the connected config carries UDP on its own, so a STUN (UDP) ping fits: SSH
     * (direct, payload, SSL, SlowDNS) and Hysteria2, or a custom JSON config whose proxy
     * outbounds are all Hysteria2 (Xray names it `hysteria`). VLESS, VMess, Trojan, groups and
     * other custom configs get the HTTP ping.
     */
    fun carriesUdp(type: EConfigType?, customRaw: String? = null): Boolean = when (type) {
        EConfigType.SSH, EConfigType.HYSTERIA2, EConfigType.HYSTERIA -> true
        EConfigType.CUSTOM -> customProxyProtocols(customRaw).let { protocols ->
            protocols.isNotEmpty() && protocols.all { it == "hysteria" || it == "hysteria2" }
        }
        else -> false
    }

    /** The method actually used: [Method.AUTO] becomes STUN or HTTP; a manual choice stays. */
    fun resolveMethod(method: Method, carriesUdp: Boolean): Method = when (method) {
        Method.AUTO -> if (carriesUdp) Method.STUN else Method.HTTP
        else -> method
    }

    const val DEFAULT_STUN_TARGET = "stun.cloudflare.com:3478"
    const val DEFAULT_INTERVAL_SEC = 5
    const val DEFAULT_TIMEOUT_SEC = 10
    const val DEFAULT_FAIL_LIMIT = 3

    data class Config(
        val mode: Mode = Mode.RESET,
        val method: Method = Method.AUTO,
        val stunTarget: String = DEFAULT_STUN_TARGET,
        val intervalSec: Int = DEFAULT_INTERVAL_SEC,
        val timeoutSec: Int = DEFAULT_TIMEOUT_SEC,
        val failLimit: Int = DEFAULT_FAIL_LIMIT,
    ) {
        /** Aggressive: at most every 2 s and a reset after at most 2 failures. */
        val effectiveIntervalSec: Int
            get() = if (mode == Mode.AGGRESSIVE) minOf(intervalSec, 2).coerceAtLeast(1) else intervalSec.coerceIn(1, 3600)

        val effectiveFailLimit: Int
            get() = if (mode == Mode.AGGRESSIVE) minOf(failLimit, 2).coerceAtLeast(1) else failLimit.coerceIn(1, 100)

        val resets: Boolean get() = mode == Mode.RESET || mode == Mode.AGGRESSIVE
    }

    /** "host:port" → pair; the port defaults to 3478. */
    /** After falling back to HTTP because STUN got no reply, STUN is tried again this often. */
    const val UDP_RETRY_MS = 60_000L

    /** True when a fallen-back STUN check is due again (the first reply on a slow link can be late). */
    fun shouldRetryUdp(fellBack: Boolean, lastTryAt: Long, now: Long, everyMs: Long = UDP_RETRY_MS): Boolean =
        fellBack && now - lastTryAt >= everyMs

    fun parseTarget(target: String): Pair<String, Int>? {
        val t = target.trim()
        if (t.isEmpty()) return null
        val idx = t.lastIndexOf(':')
        if (idx <= 0) return t to 3478
        val port = t.substring(idx + 1).toIntOrNull() ?: return null
        return t.substring(0, idx).trim('[', ']') to port
    }

    // ---------- STUN (RFC 5389) ----------

    private const val MAGIC_COOKIE = 0x2112A442

    fun newTransactionId(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(12).also { random.nextBytes(it) }

    /** A Binding Request with no attributes. */
    fun stunBindingRequest(txn: ByteArray): ByteArray {
        require(txn.size == 12)
        val out = ByteArray(20)
        out[0] = 0x00; out[1] = 0x01 // Binding Request
        out[2] = 0x00; out[3] = 0x00 // length
        out[4] = (MAGIC_COOKIE ushr 24).toByte()
        out[5] = (MAGIC_COOKIE ushr 16).toByte()
        out[6] = (MAGIC_COOKIE ushr 8).toByte()
        out[7] = MAGIC_COOKIE.toByte()
        txn.copyInto(out, 8)
        return out
    }

    /** True for a Binding Success Response to [txn]. */
    fun isStunSuccess(data: ByteArray, len: Int, txn: ByteArray): Boolean {
        if (len < 20) return false
        if (data[0].toInt() != 0x01 || data[1].toInt() != 0x01) return false
        for (i in 0 until 12) if (data[8 + i] != txn[i]) return false
        return true
    }

    // ---------- SOCKS5 UDP framing (RFC 1928 §7) ----------

    /** RSV RSV FRAG ATYP=domain LEN NAME PORT DATA. */
    fun socksUdpPacket(host: String, port: Int, payload: ByteArray): ByteArray {
        val name = host.toByteArray(Charsets.US_ASCII)
        require(name.size in 1..255)
        val out = ByteArrayOutputStream(7 + name.size + payload.size)
        out.write(0); out.write(0); out.write(0); out.write(3)
        out.write(name.size); out.write(name)
        out.write(port ushr 8); out.write(port and 0xff)
        out.write(payload)
        return out.toByteArray()
    }

    /** Offset of the data in a SOCKS5 UDP reply, or -1. */
    fun socksUdpDataOffset(data: ByteArray, len: Int): Int {
        if (len < 4 || data[2].toInt() != 0) return -1
        val off = when (data[3].toInt()) {
            1 -> 4 + 4 + 2
            4 -> 4 + 16 + 2
            3 -> if (len < 5) return -1 else 4 + 1 + (data[4].toInt() and 0xff) + 2
            else -> return -1
        }
        return if (off <= len) off else -1
    }

    // ---------- policy ----------

    /** Counts consecutive failures; [record] returns true when a reset is due (then restarts the count). */
    class FailureCounter(private val limit: Int) {
        var consecutive = 0
            private set

        fun record(success: Boolean): Boolean {
            if (success) {
                consecutive = 0
                return false
            }
            consecutive++
            if (consecutive >= limit) {
                consecutive = 0
                return true
            }
            return false
        }
    }

    /**
     * Whether a result goes into the connection log: the first one, any failure, a recovery,
     * a change of more than 30 %, or a summary once a minute. Keeps a 1-s ping from flooding it.
     */
    fun shouldLog(previous: Long?, current: Long, msSinceLastLog: Long): Boolean = when {
        previous == null -> true
        current < 0 || previous < 0 -> true
        msSinceLastLog >= 60_000L -> true
        previous == 0L -> true
        else -> kotlin.math.abs(current - previous).toDouble() / previous > 0.30
    }
}

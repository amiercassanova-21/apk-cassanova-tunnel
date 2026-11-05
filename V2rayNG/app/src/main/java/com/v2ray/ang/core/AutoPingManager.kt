package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.ConnLog
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.TrafficTracker
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

/**
 * Auto Ping in the core daemon: while connected, pings through the local SOCKS inbound every few
 * seconds (STUN over UDP, or HTTP). It keeps the link from looking idle to the operator, feeds the
 * ping shown on the home screen and widget, writes the connection log, and in Reset / Aggressive
 * mode restarts the connection after repeated failures. Owned by [CoreServiceManager]: started
 * after the core is up, stopped with it.
 */
object AutoPingManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun loadConfig(): AutoPing.Config = AutoPing.Config(
        mode = AutoPing.Mode.from(MmkvManager.decodeSettingsString(AppConfig.PREF_AUTOPING_MODE)),
        method = AutoPing.Method.from(MmkvManager.decodeSettingsString(AppConfig.PREF_AUTOPING_METHOD)),
        stunTarget = MmkvManager.decodeSettingsString(AppConfig.PREF_AUTOPING_TARGET)?.takeIf { it.isNotBlank() }
            ?: AutoPing.DEFAULT_STUN_TARGET,
        intervalSec = MmkvManager.decodeSettingsString(AppConfig.PREF_AUTOPING_INTERVAL)?.toIntOrNull()
            ?: AutoPing.DEFAULT_INTERVAL_SEC,
        timeoutSec = MmkvManager.decodeSettingsString(AppConfig.PREF_AUTOPING_TIMEOUT)?.toIntOrNull()
            ?: AutoPing.DEFAULT_TIMEOUT_SEC,
        failLimit = MmkvManager.decodeSettingsString(AppConfig.PREF_AUTOPING_FAIL_LIMIT)?.toIntOrNull()
            ?: AutoPing.DEFAULT_FAIL_LIMIT,
    )

    @Synchronized
    fun start(context: Context, onReset: () -> Unit) {
        stop()
        val cfg = loadConfig()
        if (cfg.mode == AutoPing.Mode.OFF) {
            ConnLog.info(context.getString(R.string.connlog_autoping_off))
            return
        }
        val modeName = context.resources.getStringArray(R.array.autoping_mode_entries)
            .getOrNull(AutoPing.Mode.entries.indexOf(cfg.mode)).orEmpty()
        ConnLog.info(context.getString(R.string.connlog_autoping_on, modeName, cfg.method.key.uppercase(), cfg.effectiveIntervalSec))
        job = scope.launch { loop(context, cfg, onReset) }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.loop(context: Context, cfg: AutoPing.Config, onReset: () -> Unit) {
        val counter = AutoPing.FailureCounter(cfg.effectiveFailLimit)
        var previous: Long? = null
        var lastLog = 0L
        var stun: SocksUdpClient? = null
        var stunEverOk = false
        var udpUnavailable = cfg.method != AutoPing.Method.STUN
        val target = AutoPing.parseTarget(cfg.stunTarget) ?: AutoPing.parseTarget(AutoPing.DEFAULT_STUN_TARGET)!!
        val timeoutMs = cfg.timeoutSec.coerceIn(1, 60) * 1000
        delay(FIRST_DELAY_MS)
        try {
            while (isActive) {
                var ms = -1L
                if (!udpUnavailable) {
                    ms = try {
                        val client = stun ?: SocksUdpClient(SettingsManager.getSocksPort(), timeoutMs).also { stun = it }
                        client.stunPing(target.first, target.second)
                    } catch (e: Exception) {
                        stun?.close()
                        stun = null
                        -1L
                    }
                    if (ms >= 0 && !stunEverOk) {
                        stunEverOk = true
                        ConnLog.ok(context.getString(R.string.connlog_udp_ok))
                    }
                }
                if (ms < 0 && (udpUnavailable || !stunEverOk)) {
                    // HTTP method, or UDP never worked on this server: ping over TCP instead of
                    // resetting a link that is fine but has no UDP (e.g. SSH without udpgw).
                    val http = httpPing(timeoutMs)
                    if (http >= 0 && !udpUnavailable) {
                        udpUnavailable = true
                        ConnLog.warn(context.getString(R.string.connlog_udp_unavailable))
                    }
                    ms = http
                }

                TrafficTracker.recordPing(ms)
                val now = System.currentTimeMillis()
                if (AutoPing.shouldLog(previous, ms, now - lastLog)) {
                    if (ms >= 0) ConnLog.ok(context.getString(R.string.connlog_autoping_result, ms))
                    else ConnLog.error(context.getString(R.string.connlog_autoping_failed))
                    lastLog = now
                }
                previous = ms

                if (cfg.resets && counter.record(ms >= 0)) {
                    ConnLog.warn(context.getString(R.string.connlog_autoping_reset, cfg.effectiveFailLimit))
                    stun?.close()
                    stun = null
                    try {
                        onReset()
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "AutoPing: reset failed", e)
                    }
                    delay(RESET_GRACE_MS)
                    continue
                }
                delay(cfg.effectiveIntervalSec * 1000L)
            }
        } finally {
            stun?.close()
        }
    }

    private fun httpPing(timeoutMs: Int): Long {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SettingsManager.getSocksPort()))
        return try {
            val start = System.currentTimeMillis()
            val conn = URL(SettingsManager.getDelayTestUrl()).openConnection(proxy) as HttpURLConnection
            try {
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.useCaches = false
                conn.instanceFollowRedirects = false
                if (conn.responseCode in 200..399) System.currentTimeMillis() - start else -1L
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private const val FIRST_DELAY_MS = 3_000L
    private const val RESET_GRACE_MS = 10_000L
}

/**
 * Minimal SOCKS5 UDP client (UDP ASSOCIATE) against the local inbound, kept open between pings.
 * Supports username/password when the inbound has them.
 */
internal class SocksUdpClient(port: Int, private val timeoutMs: Int) : Closeable {
    private val ctrl = Socket()
    private val udp: DatagramSocket
    private val relay: InetSocketAddress

    init {
        try {
            ctrl.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
            ctrl.soTimeout = timeoutMs
            val out = ctrl.getOutputStream()
            val inp = DataInputStream(ctrl.getInputStream())
            val user = SettingsManager.getSocksUsername()
            val pass = SettingsManager.getSocksPassword()
            out.write(if (user != null && pass != null) byteArrayOf(5, 2, 0, 2) else byteArrayOf(5, 1, 0))
            out.flush()
            if (inp.readUnsignedByte() != 5) error("not socks5")
            when (inp.readUnsignedByte()) {
                0 -> Unit
                2 -> {
                    val u = user.orEmpty().toByteArray()
                    val p = pass.orEmpty().toByteArray()
                    out.write(byteArrayOf(1, u.size.toByte()) + u + byteArrayOf(p.size.toByte()) + p)
                    out.flush()
                    inp.readUnsignedByte()
                    if (inp.readUnsignedByte() != 0) error("socks auth failed")
                }

                else -> error("no socks method")
            }
            out.write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
            out.flush()
            if (inp.readUnsignedByte() != 5) error("bad reply")
            if (inp.readUnsignedByte() != 0) error("udp associate refused")
            inp.readUnsignedByte()
            val bindHost = when (inp.readUnsignedByte()) {
                1 -> InetAddress.getByAddress(ByteArray(4).also { inp.readFully(it) })
                4 -> InetAddress.getByAddress(ByteArray(16).also { inp.readFully(it) })
                3 -> InetAddress.getByName(String(ByteArray(inp.readUnsignedByte()).also { inp.readFully(it) }))
                else -> error("bad address type")
            }
            val bindPort = inp.readUnsignedShort()
            relay = InetSocketAddress(if (bindHost.isAnyLocalAddress) InetAddress.getByName("127.0.0.1") else bindHost, bindPort)
            udp = DatagramSocket()
            udp.soTimeout = timeoutMs
        } catch (e: Exception) {
            try {
                ctrl.close()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    /** Round trip of one STUN Binding Request in ms; throws on timeout or a bad reply. */
    fun stunPing(host: String, port: Int): Long {
        val txn = AutoPing.newTransactionId()
        val packet = AutoPing.socksUdpPacket(host, port, AutoPing.stunBindingRequest(txn))
        val start = System.currentTimeMillis()
        udp.send(DatagramPacket(packet, packet.size, relay))
        val buf = ByteArray(1500)
        while (true) {
            val reply = DatagramPacket(buf, buf.size)
            udp.receive(reply)
            val off = AutoPing.socksUdpDataOffset(buf, reply.length)
            if (off < 0) continue
            val data = buf.copyOfRange(off, reply.length)
            if (AutoPing.isStunSuccess(data, data.size, txn)) return System.currentTimeMillis() - start
            if (System.currentTimeMillis() - start > timeoutMs) error("stun timeout")
        }
    }

    override fun close() {
        try {
            udp.close()
        } catch (_: Exception) {
        }
        try {
            ctrl.close()
        } catch (_: Exception) {
        }
    }
}

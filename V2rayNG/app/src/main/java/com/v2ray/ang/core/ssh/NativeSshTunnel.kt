package com.v2ray.ang.core.ssh

import com.v2ray.ang.handler.ConnLog
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the native SSH engine (`libcassh.so`, built from /cassh in Go with golang.org/x/crypto/ssh).
 * It serves the same SOCKS5 port as [SshTunnel], but every channel has its own flow control, so
 * one slow or stuck connection no longer stalls the whole session the way JSch's single reader
 * thread can. The config goes in on stdin (credentials stay out of the process arguments); the
 * engine exits when stdin closes. SlowDNS still uses the bundled dnstt client, owned here.
 */
class NativeSshTunnel(
    private val binary: File,
    private val settings: SshSettings,
    private val socksPort: Int,
    private val dnsttBinary: File?,
    private val logger: SshLogger,
    private val onStatus: (SshStatus) -> Unit = {},
    private val onBanner: (String) -> Unit = {},
) : SshEngine {
    private val running = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var dnstt: DnsttClient? = null
    private var watchdog: Thread? = null

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            var dnsttPort = 0
            if (settings.mode == SshMode.SLOWDNS) {
                val bin = dnsttBinary ?: error("dnstt binary missing")
                dnsttPort = SshTunnel.freePort()
                dnstt = DnsttClient(bin, settings, dnsttPort, logger).also { it.start() }
            }
            val p = ProcessBuilder(binary.absolutePath).redirectErrorStream(true).start()
            process = p
            stdin = p.outputStream.also {
                it.write((configJson(settings, socksPort, dnsttPort) + "\n").toByteArray(Charsets.UTF_8))
                it.flush()
            }
            SshSocksServer.thread("cassh-out") { readOutput(p) }
            if (settings.mode == SshMode.SLOWDNS) {
                watchdog = SshSocksServer.thread("cassh-dnstt-watch") { watchDnstt() }
            }
            logger.log("Native SSH engine started")
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    private fun readOutput(p: Process) {
        try {
            p.inputStream.bufferedReader().forEachLine { line ->
                val event = parseEngineLine(line)
                when (event) {
                    is EngineLine.Status -> {
                        connected.set(event.status == SshStatus.CONNECTED)
                        onStatus(event.status)
                    }

                    is EngineLine.Log -> logger.log(event.text)
                    is EngineLine.Banner -> onBanner(event.html)
                    null -> Unit
                }
            }
        } catch (_: Exception) {
        }
        connected.set(false)
        if (running.get()) logger.log("Native SSH engine exited (code ${runCatching { p.exitValue() }.getOrNull()})")
    }

    private fun watchDnstt() {
        while (running.get()) {
            try {
                Thread.sleep(DNSTT_CHECK_MS)
            } catch (_: InterruptedException) {
                return
            }
            val d = dnstt ?: return
            if (running.get() && !d.isAlive()) {
                try {
                    d.start()
                } catch (e: Exception) {
                    logger.log("SlowDNS restart failed: ${e.message}")
                }
            }
        }
    }

    override fun stop() {
        if (!running.getAndSet(false) && process == null && dnstt == null) return
        watchdog?.interrupt()
        watchdog = null
        try {
            stdin?.close()
        } catch (_: Exception) {
        }
        stdin = null
        process?.destroy()
        process = null
        dnstt?.stop()
        dnstt = null
        connected.set(false)
        logger.log("SSH stopped")
    }

    override fun isConnected(): Boolean = connected.get() && process?.isAlive == true

    /** One line of engine output. */
    sealed interface EngineLine {
        data class Status(val status: SshStatus) : EngineLine
        data class Log(val text: String) : EngineLine
        data class Banner(val html: String) : EngineLine
    }

    companion object {
        const val LIB_NAME = "libcassh.so"
        private const val DNSTT_CHECK_MS = 2_000L

        /**
         * The engine config: payloads are expanded here with [SshPayload] (one implementation for
         * both engines) and every rotation step from [SshSettings.variant] is listed. Built by
         * hand, not with reflection, so code shrinking cannot rename the keys.
         */
        fun configJson(settings: SshSettings, socksPort: Int, dnsttPort: Int): String {
            val variants = (0 until settings.variantCount).joinToString(",", "[", "]") { i ->
                val v = settings.variant(i)
                val chunks = if (v.mode.usesPayload && v.payload.isNotBlank()) {
                    SshPayload.expand(v.payload, v.host, v.port).joinToString(",", "[", "]") {
                        "{\"text\":${jsonString(String(it.bytes, Charsets.ISO_8859_1))},\"delayMs\":${it.delayBeforeMs}}"
                    }
                } else {
                    "[]"
                }
                "{\"chunks\":$chunks,\"sni\":${jsonString(v.sni)},\"proxyHost\":${jsonString(v.proxyHost)}}"
            }
            return buildString {
                append("{\"host\":").append(jsonString(settings.host))
                append(",\"port\":").append(settings.port)
                append(",\"user\":").append(jsonString(settings.username))
                append(",\"pass\":").append(jsonString(settings.password))
                append(",\"mode\":").append(jsonString(settings.mode.key))
                append(",\"proxyPort\":").append(settings.proxyPort)
                append(",\"dnsttPort\":").append(dnsttPort)
                append(",\"udpgwPort\":").append(settings.udpgwPort)
                append(",\"listen\":").append(jsonString("${SshTransport.LOOPBACK}:$socksPort"))
                append(",\"variants\":").append(variants)
                append("}")
            }
        }

        /** JSON string literal; control characters are escaped, everything else kept as is. */
        fun jsonString(value: String): String = buildString(value.length + 2) {
            append('"')
            for (c in value) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else -> append(c)
                }
            }
            append('"')
        }

        /** "STATUS CONNECTED" / "LOG text"; null for anything else. */
        fun parseEngineLine(line: String): EngineLine? = when {
            line.startsWith("STATUS ") -> when (line.removePrefix("STATUS ").trim()) {
                "CONNECTING" -> EngineLine.Status(SshStatus.CONNECTING)
                "CONNECTED" -> EngineLine.Status(SshStatus.CONNECTED)
                "RECONNECTING" -> EngineLine.Status(SshStatus.RECONNECTING)
                else -> null
            }

            line.startsWith("LOG ") -> EngineLine.Log(line.removePrefix("LOG "))
            line.startsWith("BANNER ") -> EngineLine.Banner(ConnLog.unescape(line.removePrefix("BANNER ")))
            line.isBlank() -> null
            else -> EngineLine.Log(line)
        }
    }
}

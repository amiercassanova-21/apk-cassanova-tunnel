package com.v2ray.ang.core.ssh

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Runs the bundled dnstt client (SlowDNS). It listens on 127.0.0.1:[localPort] and carries that
 * TCP stream over DNS to the dnstt server, which forwards it to the SSH server.
 */
class DnsttClient(
    private val binary: File,
    private val settings: SshSettings,
    private val localPort: Int,
    private val logger: SshLogger,
) {
    private var process: Process? = null

    fun start() {
        val cmd = listOf(binary.absolutePath) + buildArgs(settings, localPort)
        logger.log("SlowDNS: ${settings.dnsResolver.trim()} type ${settings.dnsRecordType.uppercase()} ns ${settings.dnsNameserver}")
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        process = p
        SshSocksServer.thread("dnstt-log") {
            try {
                p.inputStream.bufferedReader().forEachLine { logger.log("dnstt: $it") }
            } catch (_: Exception) {
            }
        }
        waitListening()
    }

    private fun waitListening() {
        val deadline = System.currentTimeMillis() + START_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val p = process ?: return
            if (!p.isAlive) throw IllegalStateException("dnstt exited with code ${p.exitValue()}")
            try {
                Socket().use { it.connect(InetSocketAddress(SshTransport.LOOPBACK, localPort), 200) }
                return
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        throw IllegalStateException("dnstt did not start listening")
    }

    fun isAlive(): Boolean = process?.isAlive == true

    fun stop() {
        process?.destroy()
        process = null
    }

    companion object {
        private const val START_WAIT_MS = 5_000L

        /**
         * Resolver formats: "8.8.8.8", "8.8.8.8:53", "udp://1.1.1.1", "https://dns.google/dns-query",
         * "tls://1.1.1.1", "tls://dns.google:853".
         */
        fun buildArgs(settings: SshSettings, localPort: Int): List<String> {
            val resolver = settings.dnsResolver.trim()
            val args = ArrayList<String>()
            when {
                resolver.startsWith("https://", true) -> args += listOf("-doh", resolver)
                resolver.startsWith("tls://", true) -> args += listOf("-dot", withPort(resolver.substring(6), 853))
                resolver.startsWith("dot://", true) -> args += listOf("-dot", withPort(resolver.substring(6), 853))
                resolver.startsWith("udp://", true) -> args += listOf("-udp", withPort(resolver.substring(6), 53))
                else -> args += listOf("-udp", withPort(resolver, 53))
            }
            val type = if (settings.dnsRecordType.equals(SshSettings.DNS_TYPE_NULL, true)) {
                SshSettings.DNS_TYPE_NULL
            } else {
                SshSettings.DNS_TYPE_TXT
            }
            args += listOf("-type", type)
            args += listOf("-pubkey", settings.dnsPublicKey.trim())
            args += settings.dnsNameserver.trim()
            args += "${SshTransport.LOOPBACK}:$localPort"
            return args
        }

        /** Adds [defaultPort] unless the address already has one; IPv6 literals get brackets. */
        fun withPort(address: String, defaultPort: Int): String {
            val a = address.trim().trimEnd('/')
            if (a.startsWith("[")) return if (a.contains("]:")) a else "$a:$defaultPort"
            val colons = a.count { it == ':' }
            return when {
                colons == 1 -> a
                colons > 1 -> "[$a]:$defaultPort"
                else -> "$a:$defaultPort"
            }
        }
    }
}

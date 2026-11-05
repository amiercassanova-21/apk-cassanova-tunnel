package com.v2ray.ang.core.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

enum class SshStatus { CONNECTING, CONNECTED, RECONNECTING }

/** An SSH engine exposing the tunnel as a local SOCKS5 proxy: Java (JSch) or native (Go). */
interface SshEngine {
    fun start()
    fun stop()
    fun isConnected(): Boolean
}

/**
 * SSH tunnel exposed as a local SOCKS5 proxy on 127.0.0.1:[socksPort].
 * Keeps the SSH session alive and reconnects it until [stop] is called.
 */
class SshTunnel(
    private val settings: SshSettings,
    private val socksPort: Int,
    private val dnsttBinary: File?,
    private val logger: SshLogger,
    private val onStatus: (SshStatus) -> Unit = {},
    private val onBanner: (String) -> Unit = {},
) : SshEngine {
    private val running = AtomicBoolean(false)
    private val lock = Object()

    @Volatile
    private var session: Session? = null
    private var dnstt: DnsttClient? = null
    private var dnsttPort = 0
    private var socks: SshSocksServer? = null
    private var worker: Thread? = null
    private var everConnected = false
    // Rotation step of payload / SNI / bug host; advances after each failed attempt (B2).
    @Volatile
    private var variantIndex = 0
    private var lastStatus: SshStatus? = null

    private fun report(status: SshStatus) {
        if (status == lastStatus) return
        lastStatus = status
        onStatus(status)
    }

    /** Starts the SOCKS listener (and dnstt) right away; the SSH session connects in background. */
    override fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            if (settings.mode == SshMode.SLOWDNS) {
                val bin = dnsttBinary ?: error("dnstt binary missing")
                dnsttPort = freePort()
                dnstt = DnsttClient(bin, settings, dnsttPort, logger).also { it.start() }
            }
            socks = SshSocksServer(socksPort, settings.udpgwPort, logger, ::awaitSession).also { it.start() }
        } catch (e: Exception) {
            stop()
            throw e
        }
        worker = SshSocksServer.thread("ssh-connect") { connectLoop() }
    }

    override fun stop() {
        if (!running.getAndSet(false) && socks == null && dnstt == null) return
        worker?.interrupt()
        worker = null
        socks?.stop()
        socks = null
        session?.disconnect()
        session = null
        dnstt?.stop()
        dnstt = null
        synchronized(lock) { lock.notifyAll() }
        logger.log("SSH stopped")
    }

    override fun isConnected(): Boolean = session?.isConnected == true

    private fun awaitSession(timeoutMs: Long): Session? {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (running.get()) {
                val s = session
                if (s != null && s.isConnected) return s
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return null
                lock.wait(left)
            }
        }
        return null
    }

    private fun connectLoop() {
        var attempt = 0
        while (running.get()) {
            val current = session
            if (current != null && current.isConnected) {
                try {
                    Thread.sleep(MONITOR_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return
                }
                continue
            }
            if (current != null) {
                logger.log("SSH disconnected, reconnecting")
                current.disconnect()
                session = null
            }
            report(if (everConnected) SshStatus.RECONNECTING else SshStatus.CONNECTING)
            if (settings.mode == SshMode.SLOWDNS && dnstt?.isAlive() == false) {
                try {
                    dnstt?.start()
                } catch (e: Exception) {
                    logger.log("SlowDNS restart failed: ${e.message}")
                }
            }
            attempt++
            try {
                val variants = settings.variantCount
                if (variants > 1) logger.log("SSH trying variant ${variantIndex % variants + 1}/$variants")
                val s = openSession(settings.variant(variantIndex))
                synchronized(lock) {
                    if (!running.get()) {
                        s.disconnect()
                        return
                    }
                    session = s
                    lock.notifyAll()
                }
                logger.log("SSH connected (${s.serverVersion})")
                everConnected = true
                report(SshStatus.CONNECTED)
                attempt = 0
            } catch (e: Exception) {
                if (!running.get()) return
                logger.log("SSH connect failed (attempt $attempt): ${e.message}")
                if (settings.variantCount > 1) variantIndex++
                try {
                    Thread.sleep(backoffMs(attempt))
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun openSession(current: SshSettings): Session {
        val jsch = JSch()
        val s = jsch.getSession(current.username, current.host, current.port)
        s.setPassword(current.password)
        s.userInfo = PasswordUserInfo(current.password, onBanner)
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        // Older Dropbear/OpenSSH builds on tunnel VPS still offer only these.
        s.setConfig("server_host_key", s.getConfig("server_host_key") + ",ssh-rsa,ssh-dss")
        s.setConfig("PubkeyAcceptedAlgorithms", s.getConfig("PubkeyAcceptedAlgorithms") + ",ssh-rsa")
        s.setConfig("kex", s.getConfig("kex") + ",diffie-hellman-group14-sha1,diffie-hellman-group1-sha1")
        s.setProxy(SshTransport(current, logger, dnsttPort))
        s.setServerAliveInterval(KEEPALIVE_MS)
        s.setServerAliveCountMax(KEEPALIVE_COUNT)
        s.connect(CONNECT_TIMEOUT_MS)
        return s
    }

    private class PasswordUserInfo(
        private val pass: String,
        private val onBanner: (String) -> Unit,
    ) : UserInfo, UIKeyboardInteractive {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String = pass
        override fun promptPassword(message: String?): Boolean = true
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptYesNo(message: String?): Boolean = true
        // JSch hands the server's SSH banner here.
        override fun showMessage(message: String?) {
            if (!message.isNullOrBlank()) onBanner(message)
        }
        override fun promptKeyboardInteractive(
            destination: String?, name: String?, instruction: String?,
            prompt: Array<out String>?, echo: BooleanArray?
        ): Array<String> = Array(prompt?.size ?: 0) { pass }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val KEEPALIVE_MS = 15_000
        private const val KEEPALIVE_COUNT = 3
        private const val MONITOR_INTERVAL_MS = 1_000L

        fun backoffMs(attempt: Int): Long = minOf(30_000L, 2_000L * attempt)

        fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}

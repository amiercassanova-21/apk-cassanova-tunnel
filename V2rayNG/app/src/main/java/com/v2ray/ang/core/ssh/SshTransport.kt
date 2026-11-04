package com.v2ray.ang.core.ssh

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Opens the byte stream the SSH session runs on: plain TCP, TCP + payload,
 * TLS (with SNI), TLS + payload, or the local dnstt port for SlowDNS.
 *
 * JSch calls [connect] with the SSH host/port; this transport decides the real target.
 */
class SshTransport(
    private val settings: SshSettings,
    private val logger: SshLogger,
    /** Local dnstt listen port, only used in SLOWDNS mode. */
    private val slowDnsLocalPort: Int = 0,
) : Proxy {

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val connectTimeout = if (timeout > 0) timeout else DEFAULT_TIMEOUT_MS
        val (targetHost, targetPort) = target()
        logger.log("Connecting to $targetHost:$targetPort (${settings.mode.key})")

        var sock: Socket = Socket()
        try {
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.connect(InetSocketAddress(targetHost, targetPort), connectTimeout)

            if (settings.mode.usesTls) {
                sock = startTls(sock, targetHost, targetPort)
            }

            val out = sock.getOutputStream()
            val inp = PushbackInputStream(sock.getInputStream(), PUSHBACK_SIZE)

            if (settings.mode.usesPayload && settings.payload.isNotBlank()) {
                sendPayload(out)
                readProxyResponse(sock, inp, connectTimeout)
            }

            socket = sock
            input = inp
            output = out
        } catch (e: Exception) {
            try {
                sock.close()
            } catch (_: IOException) {
            }
            throw e
        }
    }

    private fun target(): Pair<String, Int> = when {
        settings.mode == SshMode.SLOWDNS -> LOOPBACK to slowDnsLocalPort
        settings.mode.usesPayload && settings.proxyHost.isNotBlank() && settings.proxyPort > 0 ->
            settings.proxyHost to settings.proxyPort

        else -> settings.host to settings.port
    }

    private fun startTls(plain: Socket, targetHost: String, targetPort: Int): Socket {
        val sniName = settings.sni.ifBlank { settings.host }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(TrustAllManager), SecureRandom())
        val ssl = context.socketFactory.createSocket(plain, targetHost, targetPort, true) as SSLSocket
        if (sniName.isNotBlank() && !isIpLiteral(sniName)) {
            val params = ssl.sslParameters
            params.serverNames = listOf(SNIHostName(sniName))
            ssl.sslParameters = params
        }
        ssl.startHandshake()
        logger.log("TLS ready (SNI ${sniName.ifBlank { "-" }}, ${ssl.session.protocol})")
        return ssl
    }

    private fun sendPayload(out: OutputStream) {
        val chunks = SshPayload.expand(settings.payload, settings.host, settings.port)
        for (chunk in chunks) {
            if (chunk.delayBeforeMs > 0) Thread.sleep(chunk.delayBeforeMs)
            out.write(chunk.bytes)
            out.flush()
        }
        logger.log("Payload sent (${chunks.size} part(s))")
    }

    /**
     * Reads the proxy/websocket reply that follows the payload, so the SSH banner is only sent
     * after the server switched to raw mode (servers like ws-ssh drop data sent too early).
     * Bytes that are not part of an HTTP reply are pushed back for the SSH layer.
     */
    private fun readProxyResponse(sock: Socket, inp: PushbackInputStream, timeout: Int) {
        val oldTimeout = sock.soTimeout
        sock.soTimeout = timeout
        try {
            val header = readHttpHeader(inp) ?: return
            val statusLine = header.substringBefore("\r\n")
            logger.log("Server reply: $statusLine")
        } catch (_: SocketTimeoutException) {
            logger.log("No reply to payload, continuing")
        } finally {
            sock.soTimeout = oldTimeout
        }
    }

    override fun getInputStream(): InputStream? = input

    override fun getOutputStream(): OutputStream? = output

    override fun getSocket(): Socket? = socket

    override fun close() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        input = null
        output = null
    }

    companion object {
        const val LOOPBACK = "127.0.0.1"
        private const val DEFAULT_TIMEOUT_MS = 20_000
        private const val PUSHBACK_SIZE = 64 * 1024
        private const val MAX_HEADER = 32 * 1024

        /**
         * Returns the HTTP header block (up to and including CRLFCRLF) if the stream starts with
         * "HTTP/", otherwise pushes the bytes back and returns null.
         */
        fun readHttpHeader(inp: PushbackInputStream): String? {
            val first = ByteArray(5)
            var n = 0
            while (n < first.size) {
                val r = inp.read(first, n, first.size - n)
                if (r < 0) break
                n += r
            }
            if (n < 5 || String(first, 0, 5, Charsets.ISO_8859_1) != "HTTP/") {
                if (n > 0) inp.unread(first, 0, n)
                return null
            }
            val buf = ByteArrayOutputStream()
            buf.write(first, 0, n)
            var match = 0
            val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
            while (match < 4 && buf.size() < MAX_HEADER) {
                val b = inp.read()
                if (b < 0) break
                buf.write(b)
                match = if (b.toByte() == end[match]) match + 1 else if (b.toByte() == end[0]) 1 else 0
            }
            return buf.toString(Charsets.ISO_8859_1.name())
        }

        fun isIpLiteral(host: String): Boolean =
            host.contains(':') || host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))
    }

    /** Injector servers use self-signed or mismatched certs behind an SNI bug host. */
    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

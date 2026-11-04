package com.v2ray.ang.core.ssh

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local SOCKS5 server (no auth) that forwards through the SSH session:
 * - CONNECT  -> SSH direct-tcpip channel
 * - UDP ASSOCIATE -> badvpn-udpgw over a direct-tcpip channel to 127.0.0.1:udpgwPort;
 *   when udpgw is not reachable, DNS (port 53) still works through DNS-over-TCP.
 */
class SshSocksServer(
    private val listenPort: Int,
    private val udpgwPort: Int,
    private val logger: SshLogger,
    /** Waits for a connected session, null if none came up in time. */
    private val sessionProvider: (timeoutMs: Long) -> Session?,
) {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private val clients = ConcurrentHashMap.newKeySet<java.io.Closeable>()

    val port: Int get() = server?.localPort ?: listenPort

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(LOOPBACK, listenPort), 128)
        server = ss
        running.set(true)
        thread("ssh-socks-accept") {
            while (running.get()) {
                val client = try {
                    ss.accept()
                } catch (e: IOException) {
                    if (running.get()) logger.log("SOCKS accept failed: ${e.message}")
                    break
                }
                client.tcpNoDelay = true
                clients.add(client)
                thread("ssh-socks-client") {
                    try {
                        handleClient(client)
                    } catch (_: Exception) {
                    } finally {
                        clients.remove(client)
                        closeQuietly(client)
                    }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        closeQuietly(server)
        server = null
        clients.forEach { closeQuietly(it) }
        clients.clear()
    }

    private fun handleClient(client: Socket) {
        val inp = DataInputStream(client.getInputStream())
        val out = client.getOutputStream()

        // Greeting: VER NMETHODS METHODS...
        if (inp.readUnsignedByte() != 5) return
        val nMethods = inp.readUnsignedByte()
        inp.readFully(ByteArray(nMethods))
        out.write(byteArrayOf(5, 0))
        out.flush()

        // Request: VER CMD RSV ATYP DST.ADDR DST.PORT
        if (inp.readUnsignedByte() != 5) return
        val cmd = inp.readUnsignedByte()
        inp.readUnsignedByte()
        val dstHost = readAddress(inp)
        val dstPort = inp.readUnsignedShort()

        when (cmd) {
            CMD_CONNECT -> handleConnect(client, inp, out, dstHost, dstPort)
            CMD_UDP_ASSOCIATE -> handleUdpAssociate(client, inp, out)
            else -> reply(out, REP_COMMAND_NOT_SUPPORTED)
        }
    }

    private fun handleConnect(client: Socket, inp: InputStream, out: OutputStream, host: String, port: Int) {
        val channel = try {
            openDirect(host, port)
        } catch (e: Exception) {
            reply(out, REP_HOST_UNREACHABLE)
            return
        }
        val channelCloser = java.io.Closeable { channel.disconnect() }
        clients.add(channelCloser)
        try {
            reply(out, REP_SUCCEEDED)
            val remoteIn = channel.inputStream
            val remoteOut = channel.outputStream
            val up = thread("ssh-socks-up") {
                pump(inp, remoteOut)
                try {
                    remoteOut.close()
                } catch (_: IOException) {
                }
            }
            pump(remoteIn, out)
            closeQuietly(client)
            up.join(1000)
        } finally {
            clients.remove(channelCloser)
            channel.disconnect()
        }
    }

    private fun openDirect(host: String, port: Int): ChannelDirectTCPIP {
        val session = sessionProvider(SESSION_WAIT_MS) ?: throw IOException("SSH not connected")
        val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
        channel.setHost(host)
        channel.setPort(port)
        channel.setOrgIPAddress(LOOPBACK)
        channel.setOrgPort(0)
        // Streams must be fetched before connect so no early data is lost.
        channel.inputStream
        channel.connect(CHANNEL_TIMEOUT_MS)
        return channel
    }

    private fun handleUdpAssociate(client: Socket, inp: InputStream, out: OutputStream) {
        val udp = DatagramSocket(InetSocketAddress(LOOPBACK, 0))
        val relay = UdpRelay(udp)
        clients.add(relay)
        try {
            reply(out, REP_SUCCEEDED, udp.localPort)
            thread("ssh-udp-relay") { relay.run() }
            // The association lives as long as the TCP control connection.
            val buf = ByteArray(256)
            while (inp.read(buf) >= 0) {
                // ignore
            }
        } catch (_: IOException) {
        } finally {
            relay.close()
            clients.remove(relay)
        }
    }

    /** One SOCKS UDP association multiplexed onto one udpgw connection. */
    private inner class UdpRelay(private val udp: DatagramSocket) : java.io.Closeable {
        private val closed = AtomicBoolean(false)
        private var clientAddr: SocketAddress? = null
        private var gw: ChannelDirectTCPIP? = null
        private var gwOut: OutputStream? = null
        private var gwFailed = false
        private val conIds = HashMap<String, Int>()
        private var nextConId = 1

        fun run() {
            val buf = ByteArray(65535)
            while (!closed.get()) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    udp.receive(pkt)
                } catch (_: IOException) {
                    break
                }
                clientAddr = pkt.socketAddress
                try {
                    handleDatagram(pkt.data, pkt.offset, pkt.length)
                } catch (e: Exception) {
                    logger.log("UDP relay error: ${e.message}")
                }
            }
            close()
        }

        private fun handleDatagram(data: ByteArray, off: Int, len: Int) {
            val bb = ByteBuffer.wrap(data, off, len)
            if (len < 10) return
            bb.short // RSV
            if (bb.get().toInt() != 0) return // fragments not supported
            val (dstAddr, dstPort) = when (bb.get().toInt()) {
                ATYP_IPV4 -> {
                    val a = ByteArray(4); bb.get(a); InetAddress.getByAddress(a) to (bb.short.toInt() and 0xffff)
                }

                ATYP_IPV6 -> {
                    val a = ByteArray(16); bb.get(a); InetAddress.getByAddress(a) to (bb.short.toInt() and 0xffff)
                }

                ATYP_DOMAIN -> {
                    val n = bb.get().toInt() and 0xff
                    val a = ByteArray(n); bb.get(a)
                    InetAddress.getByName(String(a, Charsets.ISO_8859_1)) to (bb.short.toInt() and 0xffff)
                }

                else -> return
            }
            val payload = ByteArray(bb.remaining())
            bb.get(payload)

            if (!gwFailed && ensureGateway()) {
                sendToGateway(dstAddr, dstPort, payload)
            } else if (dstPort == 53) {
                thread("ssh-dns-tcp") { dnsOverTcp(dstAddr, payload) }
            }
        }

        @Synchronized
        private fun ensureGateway(): Boolean {
            if (gw?.isConnected == true) return true
            return try {
                val ch = openDirect(LOOPBACK, udpgwPort)
                gw = ch
                gwOut = ch.outputStream
                val gwIn = DataInputStream(ch.inputStream)
                thread("ssh-udpgw-read") { readGateway(gwIn) }
                true
            } catch (e: Exception) {
                gwFailed = true
                logger.log("udpgw 127.0.0.1:$udpgwPort unavailable (${e.message}); DNS falls back to TCP")
                false
            }
        }

        @Synchronized
        private fun sendToGateway(addr: InetAddress, port: Int, payload: ByteArray) {
            val key = "${addr.hostAddress}:$port"
            val conId = conIds.getOrPut(key) {
                val id = nextConId
                nextConId = if (nextConId >= 0xffff) 1 else nextConId + 1
                id
            }
            val frame = Udpgw.encode(conId, addr, port, payload, dns = false)
            try {
                gwOut?.write(frame)
                gwOut?.flush()
            } catch (e: IOException) {
                logger.log("udpgw write failed: ${e.message}")
                gw?.disconnect()
                gw = null
            }
        }

        private fun readGateway(gwIn: DataInputStream) {
            try {
                while (!closed.get()) {
                    val frame = Udpgw.readFrame(gwIn) ?: break
                    val decoded = Udpgw.decode(frame) ?: continue
                    sendToClient(decoded.address, decoded.port, decoded.payload)
                }
            } catch (_: IOException) {
            }
        }

        private fun dnsOverTcp(server: InetAddress, query: ByteArray) {
            var ch: ChannelDirectTCPIP? = null
            try {
                ch = openDirect(server.hostAddress ?: return, 53)
                val o = ch.outputStream
                o.write(byteArrayOf((query.size shr 8).toByte(), query.size.toByte()))
                o.write(query)
                o.flush()
                val i = DataInputStream(ch.inputStream)
                val n = i.readUnsignedShort()
                val resp = ByteArray(n)
                i.readFully(resp)
                sendToClient(server, 53, resp)
            } catch (e: Exception) {
                logger.log("DNS over TCP failed: ${e.message}")
            } finally {
                ch?.disconnect()
            }
        }

        private fun sendToClient(addr: InetAddress, port: Int, payload: ByteArray) {
            val target = clientAddr ?: return
            val ip = addr.address
            val bb = ByteBuffer.allocate(4 + ip.size + 2 + payload.size)
            bb.putShort(0).put(0)
            bb.put(if (ip.size == 4) ATYP_IPV4.toByte() else ATYP_IPV6.toByte())
            bb.put(ip).putShort(port.toShort()).put(payload)
            try {
                udp.send(DatagramPacket(bb.array(), bb.position(), target))
            } catch (_: IOException) {
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            udp.close()
            gw?.disconnect()
        }
    }

    private fun readAddress(inp: DataInputStream): String = when (inp.readUnsignedByte()) {
        ATYP_IPV4 -> ByteArray(4).also { inp.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
        ATYP_IPV6 -> ByteArray(16).also { inp.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
        ATYP_DOMAIN -> ByteArray(inp.readUnsignedByte()).also { inp.readFully(it) }
            .toString(Charsets.ISO_8859_1)

        else -> throw IOException("bad address type")
    }

    private fun reply(out: OutputStream, rep: Int, bindPort: Int = 0) {
        out.write(byteArrayOf(5, rep.toByte(), 0, ATYP_IPV4.toByte(), 127, 0, 0, 1,
            (bindPort shr 8).toByte(), bindPort.toByte()))
        out.flush()
    }

    private fun pump(from: InputStream, to: OutputStream) {
        val buf = ByteArray(BUFFER_SIZE)
        try {
            while (true) {
                val n = from.read(buf)
                if (n < 0) break
                to.write(buf, 0, n)
                to.flush()
            }
        } catch (_: IOException) {
        }
    }

    companion object {
        const val LOOPBACK = "127.0.0.1"
        private const val CMD_CONNECT = 1
        private const val CMD_UDP_ASSOCIATE = 3
        private const val ATYP_IPV4 = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6 = 4
        private const val REP_SUCCEEDED = 0
        private const val REP_HOST_UNREACHABLE = 4
        private const val REP_COMMAND_NOT_SUPPORTED = 7
        private const val SESSION_WAIT_MS = 20_000L
        private const val CHANNEL_TIMEOUT_MS = 15_000
        private const val BUFFER_SIZE = 32 * 1024

        internal fun thread(name: String, block: () -> Unit): Thread =
            Thread(block, name).also { it.isDaemon = true; it.start() }

        internal fun closeQuietly(c: java.io.Closeable?) {
            try {
                c?.close()
            } catch (_: Exception) {
            }
        }
    }
}

/** badvpn-udpgw framing: [len u16 LE][flags u8][conid u16 LE][ip][port u16 BE][data]. */
object Udpgw {
    private const val FLAG_DNS = 1 shl 2
    private const val FLAG_IPV6 = 1 shl 3

    data class Packet(val conId: Int, val address: InetAddress, val port: Int, val payload: ByteArray)

    fun encode(conId: Int, addr: InetAddress, port: Int, payload: ByteArray, dns: Boolean): ByteArray {
        val ip = addr.address
        var flags = 0
        if (addr is Inet6Address) flags = flags or FLAG_IPV6
        if (dns) flags = flags or FLAG_DNS
        val bodyLen = 3 + ip.size + 2 + payload.size
        val bb = ByteBuffer.allocate(2 + bodyLen)
        bb.order(ByteOrder.LITTLE_ENDIAN).putShort(bodyLen.toShort())
        bb.put(flags.toByte())
        bb.putShort(conId.toShort())
        bb.put(ip)
        bb.order(ByteOrder.BIG_ENDIAN).putShort(port.toShort())
        bb.put(payload)
        return bb.array()
    }

    /** Reads one frame body, null on clean EOF. */
    fun readFrame(inp: DataInputStream): ByteArray? {
        val lo = inp.read()
        if (lo < 0) return null
        val hi = inp.readUnsignedByte()
        val len = lo or (hi shl 8)
        val body = ByteArray(len)
        inp.readFully(body)
        return body
    }

    fun decode(body: ByteArray): Packet? {
        if (body.size < 3) return null
        val bb = ByteBuffer.wrap(body)
        val flags = bb.get().toInt() and 0xff
        val conId = bb.order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
        bb.order(ByteOrder.BIG_ENDIAN)
        val ipLen = if (flags and FLAG_IPV6 != 0) 16 else 4
        if (bb.remaining() < ipLen + 2) return null
        val ip = ByteArray(ipLen).also { bb.get(it) }
        val port = bb.short.toInt() and 0xffff
        val payload = ByteArray(bb.remaining()).also { bb.get(it) }
        val addr = if (ipLen == 4) Inet4Address.getByAddress(ip) else Inet6Address.getByAddress(ip)
        return Packet(conId, addr, port, payload)
    }
}

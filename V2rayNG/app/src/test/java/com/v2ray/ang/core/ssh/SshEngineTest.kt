package com.v2ray.ang.core.ssh

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.PushbackInputStream
import java.net.InetAddress

class SshEngineTest {

    @Test
    fun payloadTagsAreExpanded() {
        val out = SshPayload.substitute("GET / [protocol][crlf]Host: [host][crlf]X: [host_port][lf][cr][crlf]", "a.com", 80)
        assertEquals("GET / HTTP/1.1\r\nHost: a.com\r\nX: a.com:80\n\r\r\n", out)
        assertEquals("CONNECT a.com:80 HTTP/1.0", SshPayload.substitute("[raw]", "a.com", 80))
        assertEquals("line1\r\nline2", SshPayload.substitute("line1\nline2", "h", 1))
    }

    @Test
    fun payloadSplitsIntoChunks() {
        val chunks = SshPayload.expand("A[split]B[delay_split]C[instant_split]D", "h", 1)
        assertEquals(listOf("A", "B", "C", "D"), chunks.map { String(it.bytes) })
        assertEquals(listOf(0L, SshPayload.SPLIT_DELAY_MS, SshPayload.DELAY_SPLIT_MS, 0L), chunks.map { it.delayBeforeMs })
    }

    @Test
    fun httpHeaderIsConsumedAndSshBannerKept() {
        val inp = PushbackInputStream(ByteArrayInputStream("HTTP/1.1 101 OK\r\nA: b\r\n\r\nSSH-2.0-x\r\n".toByteArray()), 1024)
        assertEquals("HTTP/1.1 101 OK\r\nA: b\r\n\r\n", SshTransport.readHttpHeader(inp))
        assertEquals("SSH-2.0-x\r\n", String(inp.readBytes()))

        val direct = PushbackInputStream(ByteArrayInputStream("SSH-2.0-y\r\n".toByteArray()), 1024)
        assertNull(SshTransport.readHttpHeader(direct))
        assertEquals("SSH-2.0-y\r\n", String(direct.readBytes()))
    }

    @Test
    fun dnsttArgsForEachResolverForm() {
        fun args(resolver: String, type: String = "txt") = DnsttClient.buildArgs(
            SshSettings("h", 22, "u", "p", SshMode.SLOWDNS, dnsResolver = resolver,
                dnsNameserver = "ns.a.com", dnsPublicKey = "k", dnsRecordType = type), 5000
        )
        assertEquals(listOf("-udp", "8.8.8.8:53", "-type", "txt", "-pubkey", "k", "ns.a.com", "127.0.0.1:5000"), args("8.8.8.8"))
        assertEquals(listOf("-udp", "1.1.1.1:5353"), args("udp://1.1.1.1:5353").take(2))
        assertEquals(listOf("-doh", "https://dns.google/dns-query"), args("https://dns.google/dns-query").take(2))
        assertEquals(listOf("-dot", "1.1.1.1:853"), args("tls://1.1.1.1").take(2))
        assertEquals(listOf("-dot", "dns.google:853"), args("tls://dns.google:853").take(2))
        assertEquals("null", args("8.8.8.8", "NULL")[3])
        assertEquals("[2001:4860:4860::8888]:53", DnsttClient.withPort("2001:4860:4860::8888", 53))
    }

    @Test
    fun udpgwFrameRoundTrip() {
        val frame = Udpgw.encode(258, InetAddress.getByName("1.2.3.4"), 53, byteArrayOf(9, 8), dns = false)
        assertArrayEquals(byteArrayOf(11, 0, 0, 2, 1, 1, 2, 3, 4, 0, 53, 9, 8), frame)
        val body = Udpgw.readFrame(DataInputStream(ByteArrayInputStream(frame)))!!
        val p = Udpgw.decode(body)!!
        assertEquals(258, p.conId)
        assertEquals("1.2.3.4", p.address.hostAddress)
        assertEquals(53, p.port)
        assertArrayEquals(byteArrayOf(9, 8), p.payload)
    }

    @Test
    fun validationBranches() {
        val ok = SshSettings("h", 22, "u", "p")
        assertNull(ok.validate())
        assertEquals(SshConfigError.MISSING_CREDENTIALS, ok.copy(username = "").validate())
        assertEquals(SshConfigError.MISSING_CREDENTIALS, ok.copy(password = "").validate())
        assertEquals(SshConfigError.MISSING_PAYLOAD, ok.copy(mode = SshMode.PAYLOAD).validate())
        assertNull(ok.copy(mode = SshMode.SSL).validate())
        val sd = ok.copy(mode = SshMode.SLOWDNS, dnsResolver = "8.8.8.8", dnsNameserver = "ns.a.com", dnsPublicKey = "a".repeat(64))
        assertNull(sd.validate())
        assertEquals(SshConfigError.MISSING_SLOWDNS, sd.copy(dnsPublicKey = "abc").validate())
        assertEquals(SshConfigError.MISSING_SLOWDNS, sd.copy(dnsResolver = "").validate())
        assertEquals(SshMode.DIRECT, SshMode.from(null))
        assertEquals(SshMode.SSL_PAYLOAD, SshMode.from("ssl_payload"))
    }
}

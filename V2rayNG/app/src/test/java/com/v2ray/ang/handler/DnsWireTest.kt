package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class DnsWireTest {

    @Test
    fun buildsAStandardQuery() {
        val q = DnsWire.query("www.google.com.")
        val expected = byteArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) +
            byteArrayOf(3) + "www".toByteArray() + byteArrayOf(6) + "google".toByteArray() +
            byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0, 0, 1, 0, 1)
        assertArrayEquals(expected, q)
        assertThrows(IllegalArgumentException::class.java) { DnsWire.query("a".repeat(64) + ".com") }
    }

    /** A response to [DnsWire.query] of [name] with compressed A answers (and one CNAME first). */
    private fun response(name: String, rcode: Int, ips: List<String>, cname: Boolean = false): ByteArray {
        val q = DnsWire.query(name)
        val out = ByteArrayOutputStream()
        val an = ips.size + if (cname) 1 else 0
        out.write(byteArrayOf(0, 0, 0x81.toByte(), (0x80 or rcode).toByte(), 0, 1, 0, an.toByte(), 0, 0, 0, 0))
        out.write(q, 12, q.size - 12)
        if (cname) {
            // CNAME to "x.<name>": label "x" + pointer to the question name.
            out.write(byteArrayOf(0xc0.toByte(), 12, 0, 5, 0, 1, 0, 0, 0, 60, 0, 4, 1, 'x'.code.toByte(), 0xc0.toByte(), 12))
        }
        ips.forEach { ip ->
            out.write(byteArrayOf(0xc0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4))
            ip.split('.').forEach { out.write(it.toInt()) }
        }
        return out.toByteArray()
    }

    @Test
    fun readsAddressesAndSkipsOtherRecords() {
        val a = DnsWire.parse(response("www.google.com", 0, listOf("142.250.4.100", "142.250.4.101"), cname = true))
        assertEquals(DnsWire.Answer(0, listOf("142.250.4.100", "142.250.4.101")), a)
    }

    @Test
    fun rejectsQueriesAndTruncatedMessages() {
        assertNull(DnsWire.parse(DnsWire.query("example.com")))
        val full = response("example.com", 0, listOf("1.2.3.4"))
        assertNull(DnsWire.parse(full.copyOf(full.size - 2)))
        assertNull(DnsWire.parse(ByteArray(5)))
    }

    @Test
    fun adBlockVerdict() {
        assertTrue(DnsWire.isBlocked(DnsWire.parse(response("doubleclick.net", 0, listOf("0.0.0.0"))))!!)
        assertTrue(DnsWire.isBlocked(DnsWire.parse(response("doubleclick.net", 0, listOf("127.0.0.1"))))!!)
        assertTrue(DnsWire.isBlocked(DnsWire.parse(response("doubleclick.net", 3, emptyList())))!!)
        assertTrue(DnsWire.isBlocked(DnsWire.parse(response("doubleclick.net", 5, emptyList())))!!)
        assertTrue(DnsWire.isBlocked(DnsWire.parse(response("doubleclick.net", 0, emptyList())))!!)
        assertFalse(DnsWire.isBlocked(DnsWire.parse(response("doubleclick.net", 0, listOf("142.250.4.154"))))!!)
        assertNull(DnsWire.isBlocked(DnsWire.parse(response("doubleclick.net", 2, emptyList()))))
        assertNull(DnsWire.isBlocked(null))
    }

    @Test
    fun medianOfTimes() {
        assertEquals(20L, DnsSpeedTest.median(listOf(30, 10, 20)))
        assertEquals(10L, DnsSpeedTest.median(listOf(30, 10)))
        assertNull(DnsSpeedTest.median(emptyList()))
    }
}

package com.v2ray.ang.handler

/**
 * Cassanova: the few bits of the DNS wire format (RFC 1035) the DNS test needs: build an
 * A-record query and read the answer's rcode and IPv4 addresses. Pure (unit tested).
 */
object DnsWire {

    const val TYPE_A = 1
    const val RCODE_NOERROR = 0
    const val RCODE_NXDOMAIN = 3
    const val RCODE_REFUSED = 5

    data class Answer(val rcode: Int, val ipv4: List<String>)

    /** A recursive A query for [name]; id 0 as RFC 8484 suggests for DoH. */
    fun query(name: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))
        name.trim().trimEnd('.').split('.').filter { it.isNotEmpty() }.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.size in 1..63) { "Bad label" }
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.write(byteArrayOf(0, TYPE_A.toByte(), 0, 1))
        return out.toByteArray()
    }

    /** Null when [msg] is not a well-formed response. */
    fun parse(msg: ByteArray): Answer? {
        if (msg.size < 12) return null
        fun u16(i: Int) = ((msg[i].toInt() and 0xff) shl 8) or (msg[i + 1].toInt() and 0xff)
        if (msg[2].toInt() and 0x80 == 0) return null // not a response
        val rcode = msg[3].toInt() and 0x0f
        val qd = u16(4)
        val an = u16(6)
        var pos = 12

        fun skipName(): Boolean {
            while (true) {
                if (pos >= msg.size) return false
                val len = msg[pos].toInt() and 0xff
                when {
                    len == 0 -> { pos += 1; return true }
                    len and 0xc0 == 0xc0 -> { pos += 2; return pos <= msg.size }
                    len and 0xc0 != 0 -> return false
                    else -> pos += 1 + len
                }
            }
        }

        repeat(qd) {
            if (!skipName()) return null
            pos += 4
        }
        val ips = ArrayList<String>()
        repeat(an) {
            if (!skipName() || pos + 10 > msg.size) return null
            val type = u16(pos)
            val rdLen = u16(pos + 8)
            pos += 10
            if (pos + rdLen > msg.size) return null
            if (type == TYPE_A && rdLen == 4) {
                ips.add((0 until 4).joinToString(".") { (msg[pos + it].toInt() and 0xff).toString() })
            }
            pos += rdLen
        }
        return Answer(rcode, ips)
    }

    /**
     * Whether the answer for a known ad domain shows a block: 0.0.0.0 or a loopback address,
     * NXDOMAIN, REFUSED, or no address at all. Null for other errors (no verdict).
     */
    fun isBlocked(answer: Answer?): Boolean? {
        answer ?: return null
        return when (answer.rcode) {
            RCODE_NXDOMAIN, RCODE_REFUSED -> true
            RCODE_NOERROR -> answer.ipv4.isEmpty() || answer.ipv4.any { it == "0.0.0.0" || it.startsWith("127.") }
            else -> null
        }
    }
}

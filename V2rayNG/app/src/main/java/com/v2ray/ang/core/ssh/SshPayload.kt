package com.v2ray.ang.core.ssh

/**
 * Expands an injector-style payload (HTTP Custom / HTTP Injector syntax) into the
 * byte chunks that are written to the server before the SSH handshake.
 *
 * Supported tags (case-insensitive):
 * [host] [port] [host_port] [ssh] [protocol] [ua] [method] [raw] [netData] [real_raw]
 * [crlf] [lf] [cr] [lfcr]
 * [split] (new write after a short pause), [delay_split] (new write after 1 s),
 * [instant_split] (new write, no pause).
 */
object SshPayload {

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    const val SPLIT_DELAY_MS = 200L
    const val DELAY_SPLIT_MS = 1000L

    /** One write; [delayBeforeMs] is waited before writing it. */
    data class Chunk(val bytes: ByteArray, val delayBeforeMs: Long)

    private val splitRegex = Regex("\\[(split|delay_split|instant_split)]", RegexOption.IGNORE_CASE)

    fun expand(template: String, host: String, port: Int): List<Chunk> {
        val text = substitute(template, host, port)
        val chunks = ArrayList<Chunk>()
        var delay = 0L
        var last = 0
        for (m in splitRegex.findAll(text)) {
            val part = text.substring(last, m.range.first)
            if (part.isNotEmpty()) chunks.add(Chunk(part.toByteArray(Charsets.ISO_8859_1), delay))
            delay = when (m.groupValues[1].lowercase()) {
                "delay_split" -> DELAY_SPLIT_MS
                "instant_split" -> 0L
                else -> SPLIT_DELAY_MS
            }
            last = m.range.last + 1
        }
        val tail = text.substring(last)
        if (tail.isNotEmpty()) chunks.add(Chunk(tail.toByteArray(Charsets.ISO_8859_1), delay))
        return chunks
    }

    fun substitute(template: String, host: String, port: Int): String {
        val hostPort = "$host:$port"
        val raw = "CONNECT $hostPort HTTP/1.0"
        val pairs = listOf(
            "[real_raw]" to "$raw\r\n\r\n",
            "[raw]" to raw,
            "[netdata]" to raw,
            "[method]" to "CONNECT",
            "[host_port]" to hostPort,
            "[ssh]" to hostPort,
            "[host]" to host,
            "[port]" to port.toString(),
            "[protocol]" to "HTTP/1.1",
            "[ua]" to DEFAULT_USER_AGENT,
            "[crlf]" to "\r\n",
            "[lfcr]" to "\n\r",
            "[lf]" to "\n",
            "[cr]" to "\r",
        )
        // Real line breaks typed in the editor count as CRLF, like [crlf]. Done before the
        // tags are expanded so [lf], [cr] and [lfcr] keep their exact bytes.
        var out = template.replace("\r\n", "\n").replace("\n", "\r\n")
        for ((tag, value) in pairs) {
            out = out.replace(tag, value, ignoreCase = true)
        }
        return out
    }
}

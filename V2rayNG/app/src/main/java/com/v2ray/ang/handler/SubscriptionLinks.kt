package com.v2ray.ang.handler

/**
 * Cassanova: one subscription may hold several links, one per line (e.g. the same account on
 * three servers). All are fetched and merged into the subscription's group. Pure, unit tested.
 */
object SubscriptionLinks {
    const val MAX_LINKS = 10

    /** The links in the URL field: split on line breaks and spaces, blanks and repeats dropped. */
    fun split(urlField: String?): List<String> =
        urlField.orEmpty()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Whether the field holds 1 to [MAX_LINKS] links that all pass [isValid]. */
    fun allValid(urlField: String?, isValid: (String) -> Boolean): Boolean {
        val links = split(urlField)
        return links.isNotEmpty() && links.size <= MAX_LINKS && links.all(isValid)
    }

    /** "#2 sgdo8.example.id" for messages: position plus host, never the path (it may hold a token). */
    fun label(index: Int, link: String): String {
        val host = link.substringAfter("://", link).substringBefore('/').substringBefore('?').substringAfter('@')
        return "#${index + 1} $host"
    }

    /**
     * One link's response as share links, one per line: the base64-decoded body when that holds
     * links, else the body itself; null when it holds no share link at all.
     */
    fun shareLines(body: String, decode: (String) -> String): String? {
        val text = body.trim()
        if (text.isEmpty()) return null
        val decoded = runCatching { decode(text) }.getOrDefault("")
        return when {
            decoded.contains("://") -> decoded.trim()
            text.contains("://") -> text
            else -> null
        }
    }
}

package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class SubscriptionLinksTest {

    private val a = "https://sgdo7.example.id/sub/c6c2760f-f6af-4a65-8622-cf9c9971f888"
    private val b = "https://sgdo8.example.id/sub/c6c2760f-f6af-4a65-8622-cf9c9971f888"

    @Test
    fun splitsOneLinkPerLine() {
        // As typed in the app: a blank line between the links, stray spaces, a repeat.
        assertEquals(listOf(a, b), SubscriptionLinks.split("$a\n\n  $b \n$a\n"))
        assertEquals(listOf(a), SubscriptionLinks.split(a))
        assertTrue(SubscriptionLinks.split("  \n ").isEmpty())
        assertTrue(SubscriptionLinks.split(null).isEmpty())
    }

    @Test
    fun everyLinkMustBeValidAndAtMostTen() {
        val ok: (String) -> Boolean = { it.startsWith("https://") }
        assertTrue(SubscriptionLinks.allValid("$a\n$b", ok))
        assertFalse(SubscriptionLinks.allValid("$a\nhttp://x.example/sub", ok))
        assertFalse(SubscriptionLinks.allValid("", ok))
        val eleven = (1..11).joinToString("\n") { "https://s$it.example.id/sub" }
        assertFalse(SubscriptionLinks.allValid(eleven, ok))
        assertTrue(SubscriptionLinks.allValid(eleven.lines().take(10).joinToString("\n"), ok))
    }

    @Test
    fun labelShowsPositionAndHostOnly() {
        // The path holds the account token: never in messages or logs.
        assertEquals("#2 sgdo8.example.id", SubscriptionLinks.label(1, b))
        assertEquals("#1 x.example:8443", SubscriptionLinks.label(0, "https://user:pw@x.example:8443/s?t=1"))
    }

    private val decode: (String) -> String = { text ->
        runCatching { String(Base64.getDecoder().decode(text)) }.getOrDefault("")
    }

    @Test
    fun responsesBecomeShareLines() {
        val links = "vless://u@h1:443?type=ws#A\nvless://u@h2:443?type=ws#B"
        assertEquals(links, SubscriptionLinks.shareLines(Base64.getEncoder().encodeToString(links.toByteArray()), decode))
        assertEquals(links, SubscriptionLinks.shareLines("\n$links\n", decode))
        assertNull(SubscriptionLinks.shareLines("<html>502 Bad Gateway</html>", decode))
        assertNull(SubscriptionLinks.shareLines("   ", decode))
    }
}

package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostLookupTest {

    @Test
    fun normalizeStripsSchemePortPathAndCase() {
        assertEquals("sub.example.com", HostLookup.normalizeHost("https://user@Sub.Example.COM:443/path?q=1"))
        assertEquals("example.com", HostLookup.normalizeHost("  example.com.  "))
        assertEquals("1.2.3.4", HostLookup.normalizeHost("1.2.3.4:80"))
        assertEquals("2001:db8::1", HostLookup.normalizeHost("[2001:db8::1]:443"))
        assertEquals("2001:db8::1", HostLookup.normalizeHost("2001:db8::1"))
    }

    @Test
    fun normalizeRejectsEmptyAndComments() {
        assertNull(HostLookup.normalizeHost(""))
        assertNull(HostLookup.normalizeHost("   "))
        assertNull(HostLookup.normalizeHost("# comment"))
        assertNull(HostLookup.normalizeHost("https://"))
    }

    @Test
    fun parseHostsSplitsDedupesAndLimits() {
        assertEquals(
            listOf("a.com", "b.com", "c.com"),
            HostLookup.parseHosts("a.com\nB.com, a.com\n\n c.com ;https://b.com/x")
        )
        val many = (1..30).joinToString("\n") { "h$it.com" }
        assertEquals(HostLookup.MAX_HOSTS, HostLookup.parseHosts(many).size)
        assertTrue(HostLookup.parseHosts(" \n ").isEmpty())
    }

    @Test
    fun dohAnswerKeepsOnlyRequestedType() {
        val json = """{"Status":0,"Answer":[
            {"name":"www.example.com","type":5,"data":"example.com."},
            {"name":"example.com","type":1,"data":"93.184.215.14"},
            {"name":"example.com","type":1,"data":"93.184.215.15"},
            {"name":"example.com","type":28,"data":"2606:2800:21f:cb07:6820:80da:af6b:8b2c"}]}"""
        assertEquals(listOf("93.184.215.14", "93.184.215.15"), HostLookup.parseDohAnswer(json, 1))
        assertEquals(listOf("2606:2800:21f:cb07:6820:80da:af6b:8b2c"), HostLookup.parseDohAnswer(json, 28))
        assertEquals(0, HostLookup.dohStatus(json))
    }

    @Test
    fun dohAnswerHandlesNxdomainAndGarbage() {
        val nx = """{"Status":3,"Authority":[{"name":"com","type":6,"data":"x"}]}"""
        assertTrue(HostLookup.parseDohAnswer(nx, 1).isEmpty())
        assertEquals(3, HostLookup.dohStatus(nx))
        assertTrue(HostLookup.parseDohAnswer("not json", 1).isEmpty())
        assertTrue(HostLookup.parseDohAnswer(null, 1).isEmpty())
        assertNull(HostLookup.dohStatus("<html>"))
    }

    @Test
    fun dohUrlsAndSourceKeys() {
        assertEquals("https://dns.google/resolve?name=a.com&type=1", HostLookup.dohUrl(HostLookup.Source.GOOGLE, "a.com", 1))
        assertEquals(
            "https://cloudflare-dns.com/dns-query?name=a.com&type=28",
            HostLookup.dohUrl(HostLookup.Source.CLOUDFLARE, "a.com", 28)
        )
        assertEquals(HostLookup.Source.GOOGLE, HostLookup.Source.fromKey("google"))
        assertEquals(HostLookup.Source.SYSTEM, HostLookup.Source.fromKey(null))
        assertEquals(HostLookup.Source.SYSTEM, HostLookup.Source.fromKey("bogus"))
    }

    @Test
    fun historyIsLatestFirstUniqueAndCapped() {
        assertEquals(listOf("x.com", "y.com", "a.com"), HostLookup.pushHistory(listOf("a.com", "x.com"), listOf("x.com", "y.com")))
        val full = (1..10).map { "h$it.com" }
        val next = HostLookup.pushHistory(full, listOf("new.com"))
        assertEquals(HostLookup.HISTORY_SIZE, next.size)
        assertEquals("new.com", next.first())
    }
}

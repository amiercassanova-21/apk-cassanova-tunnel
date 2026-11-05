package com.v2ray.ang.core.ssh

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeSshTunnelTest {

    private val settings = SshSettings(
        host = "ssh.example.com", port = 443, username = "u", password = "p\"w\\d\n",
        mode = SshMode.SSL_PAYLOAD,
        payload = "GET / HTTP/1.1[crlf]Host: [host][crlf][split]X[crlf][crlf]\n---\nB[crlf]",
        sni = "a.com, b.com", proxyHost = "", proxyPort = 0,
    )

    @Test
    fun configIsValidJsonWithEveryVariant() {
        val json = JsonParser.parseString(NativeSshTunnel.configJson(settings, 10808, 0)).asJsonObject
        assertEquals("ssh.example.com", json["host"].asString)
        assertEquals("p\"w\\d\n", json["pass"].asString)
        assertEquals("ssl_payload", json["mode"].asString)
        assertEquals("127.0.0.1:10808", json["listen"].asString)
        val variants = json["variants"].asJsonArray
        assertEquals(2, variants.size())
        val first = variants[0].asJsonObject
        assertEquals("a.com", first["sni"].asString)
        val chunks = first["chunks"].asJsonArray
        assertEquals(2, chunks.size())
        assertEquals("GET / HTTP/1.1\r\nHost: ssh.example.com\r\n", chunks[0].asJsonObject["text"].asString)
        assertEquals(SshPayload.SPLIT_DELAY_MS, chunks[1].asJsonObject["delayMs"].asLong)
        assertEquals("b.com", variants[1].asJsonObject["sni"].asString)
    }

    @Test
    fun directModeSendsNoChunks() {
        val json = JsonParser.parseString(
            NativeSshTunnel.configJson(settings.copy(mode = SshMode.DIRECT, sni = ""), 1, 0)
        ).asJsonObject
        val v = json["variants"].asJsonArray
        assertEquals(2, v.size()) // payload alternatives still count, but carry no chunks
        assertEquals(0, v[0].asJsonObject["chunks"].asJsonArray.size())
    }

    @Test
    fun jsonStringEscapesControlCharacters() {
        assertEquals("\"a\\u0001b\\tc\"", NativeSshTunnel.jsonString("a\u0001b\tc"))
        assertEquals("\"ÿ\"", NativeSshTunnel.jsonString("ÿ"))
    }

    @Test
    fun engineLinesAreParsed() {
        assertEquals(NativeSshTunnel.EngineLine.Status(SshStatus.CONNECTED), NativeSshTunnel.parseEngineLine("STATUS CONNECTED"))
        assertEquals(NativeSshTunnel.EngineLine.Log("SSH connected"), NativeSshTunnel.parseEngineLine("LOG SSH connected"))
        assertEquals(NativeSshTunnel.EngineLine.Log("panic: x"), NativeSshTunnel.parseEngineLine("panic: x"))
        assertNull(NativeSshTunnel.parseEngineLine("STATUS ???"))
        assertNull(NativeSshTunnel.parseEngineLine(" "))
    }

    @Test
    fun nativeIsDefaultWhenPackaged() {
        assertTrue(SshTunnelManager.useNativeEngine(null, true))
        assertTrue(SshTunnelManager.useNativeEngine(AppConfig.SSH_ENGINE_NATIVE, true))
        assertFalse(SshTunnelManager.useNativeEngine(AppConfig.SSH_ENGINE_JAVA, true))
        assertFalse(SshTunnelManager.useNativeEngine(AppConfig.SSH_ENGINE_NATIVE, false))
    }
}

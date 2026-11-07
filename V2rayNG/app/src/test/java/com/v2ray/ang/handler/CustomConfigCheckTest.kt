package com.v2ray.ang.handler

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomConfigCheckTest {

    private val config = """
        {
          "observatory": {"subjectSelector": ["TLS-"]},
          "outbounds": [
            {"tag": "TLS-1 WS", "protocol": "vless",
             "streamSettings": {"network": "ws", "wsSettings": {"path": "/vless", "headers": {"Host": "a.example"}}}},
            {"tag": "TLS-1 HU", "protocol": "vless",
             "streamSettings": {"network": "httpupgrade", "wsSettings": {"path": "/up", "headers": {"Host": "b.example", "X-A": "1"}}}},
            {"tag": "direct", "protocol": "freedom"}
          ],
          "routing": {
            "balancers": [
              {"tag": "auto-tls", "selector": ["TLS-"]},
              {"tag": "auto-ntls", "selector": ["NTLS-"]}
            ],
            "rules": [
              {"outboundTag": "block", "domain": ["geosite:category-ads-all", "geosite:youtube"]},
              {"outboundTag": "direct", "ip": ["geoip:private", "geoip:us"]},
              {"balancerTag": "auto-x", "network": "tcp"},
              {"balancerTag": "auto-tls", "network": "tcp,udp"}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun findsTheSilentMistakes() {
        val issues = CustomConfigCheck.check(config)
        assertEquals(
            listOf(
                CustomConfigCheck.Issue(CustomConfigCheck.Kind.HTTPUPGRADE_WS_SETTINGS, "TLS-1 HU"),
                CustomConfigCheck.Issue(CustomConfigCheck.Kind.SELECTOR_NO_MATCH, "NTLS-"),
                CustomConfigCheck.Issue(CustomConfigCheck.Kind.RULE_UNKNOWN_OUTBOUND, "block"),
                CustomConfigCheck.Issue(CustomConfigCheck.Kind.RULE_UNKNOWN_BALANCER, "auto-x"),
            ),
            issues
        )
        assertTrue(issues.first().fixable)
        assertFalse(issues.last().fixable)
    }

    @Test
    fun liteAlsoReportsTrimmedGeoCategories() {
        val geo = CustomConfigCheck.check(config, lite = true)
            .filter { it.kind == CustomConfigCheck.Kind.LITE_GEO_MISSING }
            .map { it.subject }
        assertEquals(listOf("geosite:youtube", "geoip:us"), geo)
    }

    @Test
    fun autoFixMovesWsSettingsToHttpUpgradeSettings() {
        val fixed = CustomConfigCheck.autoFix(config)!!
        val stream = JsonParser.parseString(fixed).asJsonObject.getAsJsonArray("outbounds")[1].asJsonObject
            .getAsJsonObject("streamSettings")
        assertFalse(stream.has("wsSettings"))
        val upgrade = stream.getAsJsonObject("httpupgradeSettings")
        assertEquals("/up", upgrade.get("path").asString)
        assertEquals("b.example", upgrade.get("host").asString)
        assertEquals("1", upgrade.getAsJsonObject("headers").get("X-A").asString)
        // The ws outbound keeps its wsSettings.
        assertTrue(
            JsonParser.parseString(fixed).asJsonObject.getAsJsonArray("outbounds")[0].asJsonObject
                .getAsJsonObject("streamSettings").has("wsSettings")
        )
        assertTrue(CustomConfigCheck.check(fixed).none { it.kind == CustomConfigCheck.Kind.HTTPUPGRADE_WS_SETTINGS })
        assertNull(CustomConfigCheck.autoFix(fixed))
    }

    @Test
    fun cleanOrUnreadableConfigsGiveNoIssues() {
        val clean = """{"outbounds":[{"tag":"p","protocol":"vless"}],"routing":{"rules":[{"outboundTag":"p"}]}}"""
        assertTrue(CustomConfigCheck.check(clean, lite = true).isEmpty())
        assertTrue(CustomConfigCheck.check("not json").isEmpty())
        assertTrue(CustomConfigCheck.check(null).isEmpty())
        assertNull(CustomConfigCheck.autoFix("not json"))
        assertNull(CustomConfigCheck.autoFix(clean))
    }
}

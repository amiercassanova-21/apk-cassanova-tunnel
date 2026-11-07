package com.v2ray.ang.handler

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Cassanova: checks a custom JSON config when it is saved and reports mistakes that Xray accepts
 * silently but that break or weaken the connection. Pure (unit tested); the editor shows the
 * issues and offers [autoFix] for the fixable ones.
 */
object CustomConfigCheck {

    enum class Kind {
        /** An `httpupgrade` outbound configured through `wsSettings`: Xray ignores it. */
        HTTPUPGRADE_WS_SETTINGS,

        /** A balancer or observatory selector that matches no outbound tag. */
        SELECTOR_NO_MATCH,

        /** A routing rule pointing at an outbound tag that does not exist. */
        RULE_UNKNOWN_OUTBOUND,

        /** A routing rule pointing at a balancer tag that does not exist. */
        RULE_UNKNOWN_BALANCER,

        /** A geosite/geoip category the trimmed Lite geo files do not contain. */
        LITE_GEO_MISSING,
    }

    /** One finding; [subject] is the tag, selector or category it is about. */
    data class Issue(val kind: Kind, val subject: String) {
        val fixable: Boolean get() = kind == Kind.HTTPUPGRADE_WS_SETTINGS
    }

    /** Geo categories kept in the Lite build (must match the geotrim step in the CI workflow). */
    val LITE_GEOSITE = setOf("private", "cn", "google", "category-ads-all")
    val LITE_GEOIP = setOf("private", "id", "cn")

    private fun parse(raw: String?): JsonObject? = try {
        raw?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
    } catch (e: Exception) {
        null
    }

    private fun JsonObject.array(name: String): JsonArray? = get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonArray.objects(): List<JsonObject> = mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }

    private fun JsonArray.strings(): List<String> =
        mapNotNull { it.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isString }?.asString }

    /** A rule field that may be a string or a list of strings. */
    private fun JsonObject.stringsOf(name: String): List<String> {
        val e = get(name) ?: return emptyList()
        return when {
            e.isJsonArray -> e.asJsonArray.strings()
            e.isJsonPrimitive && e.asJsonPrimitive.isString -> listOf(e.asString)
            else -> emptyList()
        }
    }

    private fun isHttpUpgradeWithWsSettings(outbound: JsonObject): Boolean {
        val stream = outbound.obj("streamSettings") ?: return false
        return stream.string("network")?.lowercase() == "httpupgrade" &&
            stream.has("wsSettings") && !stream.has("httpupgradeSettings")
    }

    /**
     * Issues in [raw], in config order and without duplicates. JSON that does not parse gives no
     * issues: the parser reports it. With [lite], geo categories missing from Lite are reported.
     */
    fun check(raw: String?, lite: Boolean = false): List<Issue> {
        val json = parse(raw) ?: return emptyList()
        val issues = LinkedHashSet<Issue>()
        val outbounds = json.array("outbounds")?.objects().orEmpty()
        val tags = outbounds.mapNotNull { it.string("tag") }.toSet()

        outbounds.filter(::isHttpUpgradeWithWsSettings).forEach {
            issues.add(Issue(Kind.HTTPUPGRADE_WS_SETTINGS, it.string("tag").orEmpty()))
        }

        val routing = json.obj("routing")
        val balancers = routing?.array("balancers")?.objects().orEmpty()
        val selectors = balancers.flatMap { it.array("selector")?.strings().orEmpty() } +
            json.obj("observatory")?.array("subjectSelector")?.strings().orEmpty() +
            json.obj("burstObservatory")?.array("subjectSelector")?.strings().orEmpty()
        selectors.filter { selector -> tags.none { it.startsWith(selector) } }
            .forEach { issues.add(Issue(Kind.SELECTOR_NO_MATCH, it)) }

        val balancerTags = balancers.mapNotNull { it.string("tag") }.toSet()
        val rules = routing?.array("rules")?.objects().orEmpty()
        for (rule in rules) {
            rule.string("outboundTag")?.takeIf { it !in tags }?.let { issues.add(Issue(Kind.RULE_UNKNOWN_OUTBOUND, it)) }
            rule.string("balancerTag")?.takeIf { it !in balancerTags }?.let { issues.add(Issue(Kind.RULE_UNKNOWN_BALANCER, it)) }
            if (lite) {
                (rule.stringsOf("domain") + rule.stringsOf("ip")).forEach { value ->
                    missingLiteGeo(value)?.let { issues.add(Issue(Kind.LITE_GEO_MISSING, it)) }
                }
            }
        }
        return issues.toList()
    }

    /** `geosite:youtube` → itself when Lite lacks the category; null otherwise. */
    private fun missingLiteGeo(value: String): String? {
        val v = value.trim().lowercase().removePrefix("!")
        val (kept, rest) = when {
            v.startsWith("geosite:") -> LITE_GEOSITE to v.removePrefix("geosite:")
            v.startsWith("geoip:") -> LITE_GEOIP to v.removePrefix("geoip:")
            else -> return null
        }
        val category = rest.substringBefore('@')
        return if (category.isNotEmpty() && category !in kept) value.trim() else null
    }

    /**
     * [raw] with the fixable issues repaired: `wsSettings` of an `httpupgrade` outbound becomes
     * `httpupgradeSettings` (same path and host). Null when nothing changes or it does not parse.
     */
    fun autoFix(raw: String?): String? {
        val json = parse(raw) ?: return null
        var changed = false
        json.array("outbounds")?.objects().orEmpty().filter(::isHttpUpgradeWithWsSettings).forEach { outbound ->
            val stream = outbound.obj("streamSettings") ?: return@forEach
            val ws = stream.remove("wsSettings")
            val upgrade = JsonObject()
            ws?.takeIf { it.isJsonObject }?.asJsonObject?.let { w ->
                w.get("path")?.let { upgrade.add("path", it) }
                val host = w.string("host") ?: w.obj("headers")?.let { it.string("Host") ?: it.string("host") }
                host?.let { upgrade.addProperty("host", it) }
                w.obj("headers")?.let { headers ->
                    val rest = JsonObject()
                    headers.entrySet().filter { !it.key.equals("host", ignoreCase = true) }.forEach { rest.add(it.key, it.value) }
                    if (rest.size() > 0) upgrade.add("headers", rest)
                }
            }
            stream.add("httpupgradeSettings", upgrade)
            changed = true
        }
        return if (changed) GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) else null
    }
}

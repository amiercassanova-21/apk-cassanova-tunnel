package com.v2ray.ang.handler

import androidx.annotation.StringRes
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R

/**
 * Cassanova: ready-made DNS choices. A preset is only a pair of values for the existing
 * settings [AppConfig.PREF_VPN_DNS] (plain IPs, used by the phone and by SSH/SlowDNS) and
 * [AppConfig.PREF_REMOTE_DNS] (DoH, used by the core); nothing new is stored. Which preset is
 * active is read back from those two values, so editing either by hand shows "Custom".
 *
 * Addresses checked against the AdGuard DNS provider list (adguard-dns.io/kb/general/dns-providers)
 * on 2026-11-19. Mullvad is left out: its public DNS closed on 2026-11-02.
 */
object DnsPresets {

    enum class Category(@StringRes val labelRes: Int) {
        FAST(R.string.dns_category_fast),
        BALANCED(R.string.dns_category_balanced),
        ADBLOCK(R.string.dns_category_adblock),
        FAMILY(R.string.dns_category_family),
    }

    data class Preset(
        val id: String,
        @StringRes val nameRes: Int,
        val category: Category,
        val vpnDns: List<String>,
        val remoteDns: String,
    ) {
        val vpnDnsValue: String get() = vpnDns.joinToString(",")
    }

    val ALL: List<Preset> = listOf(
        Preset("cloudflare", R.string.dns_preset_cloudflare, Category.FAST,
            listOf("1.1.1.1", "1.0.0.1"), "https://cloudflare-dns.com/dns-query"),
        Preset("google", R.string.dns_preset_google, Category.FAST,
            listOf("8.8.8.8", "8.8.4.4"), "https://dns.google/dns-query"),
        Preset("quad9", R.string.dns_preset_quad9, Category.BALANCED,
            listOf("9.9.9.9", "149.112.112.112"), "https://dns.quad9.net/dns-query"),
        Preset("cloudflare_security", R.string.dns_preset_cloudflare_security, Category.BALANCED,
            listOf("1.1.1.2", "1.0.0.2"), "https://security.cloudflare-dns.com/dns-query"),
        Preset("adguard", R.string.dns_preset_adguard, Category.ADBLOCK,
            listOf("94.140.14.14", "94.140.15.15"), "https://dns.adguard-dns.com/dns-query"),
        Preset("controld_ads", R.string.dns_preset_controld_ads, Category.ADBLOCK,
            listOf("76.76.2.2"), "https://freedns.controld.com/p2"),
        Preset("adguard_family", R.string.dns_preset_adguard_family, Category.FAMILY,
            listOf("94.140.14.15", "94.140.15.16"), "https://family.adguard-dns.com/dns-query"),
        Preset("cloudflare_family", R.string.dns_preset_cloudflare_family, Category.FAMILY,
            listOf("1.1.1.3", "1.0.0.3"), "https://family.cloudflare-dns.com/dns-query"),
    )

    /** The app default ([AppConfig.DNS_VPN] + [AppConfig.DNS_PROXY]) is Cloudflare. */
    const val DEFAULT_ID = "cloudflare"

    fun byId(id: String?): Preset? = ALL.firstOrNull { it.id == id }

    private fun sameUrl(a: String, b: String): Boolean =
        a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)

    /**
     * The preset matching the stored values, or null for "Custom". A blank remote DNS means the
     * app default (the core falls back to it); a blank VPN DNS gives the phone no DNS server, so
     * it never matches. The VPN DNS may hold any subset of the preset's addresses (the old
     * default "1.1.1.1" is still Cloudflare).
     */
    fun detect(vpnDns: String?, remoteDns: String?): Preset? {
        val vpn = vpnDns.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (vpn.isEmpty()) return null
        val remote = remoteDns?.takeIf { it.isNotBlank() } ?: AppConfig.DNS_PROXY
        return ALL.firstOrNull { p -> p.vpnDns.containsAll(vpn) && sameUrl(p.remoteDns, remote) }
    }

    /** The first DoH (https) address in a remote DNS value, for testing a custom setting. */
    fun customDohUrl(remoteDns: String?): String? =
        remoteDns.orEmpty().split(",").map { it.trim() }.firstOrNull { it.startsWith("https://", ignoreCase = true) }

    /** Host of a DoH URL for logs and labels (never the path: it may carry an account ID). */
    fun hostOf(url: String?): String? {
        val u = url?.trim()?.split(",")?.firstOrNull()?.trim().orEmpty()
        if (u.isEmpty()) return null
        val rest = u.substringAfter("://", u)
        return rest.substringBefore('/').substringBefore('?').substringAfterLast('@').takeIf { it.isNotEmpty() }
    }
}

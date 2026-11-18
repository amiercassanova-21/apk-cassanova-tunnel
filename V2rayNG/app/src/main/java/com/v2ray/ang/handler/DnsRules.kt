package com.v2ray.ang.handler

/**
 * Cassanova: the extra DNS-related rules built into a connection's config (pure, unit tested):
 * the user's always-block / always-allow domain lists, the ad-block routing rule that goes
 * with an ad-blocking DNS choice, and the DNS query strategy when IPv6 is off.
 */
object DnsRules {

    /** Ads category kept in both the full and the Lite geosite file. */
    const val ADS_GEOSITE = "geosite:category-ads-all"

    /** Unfiltered resolver for always-allowed domains (an ad-blocking DNS would block them). */
    const val ALLOW_DNS = "https://cloudflare-dns.com/dns-query"

    private val xrayPrefixes = listOf("domain:", "full:", "regexp:", "keyword:", "geosite:", "ext:", "dotless:")

    /**
     * Domains typed by the user (commas, spaces or new lines between them) as Xray domain rules.
     * A plain name becomes `domain:<name>` (the name and its subdomains); a pasted link is cut
     * down to its host; entries with an Xray prefix are kept as typed.
     */
    fun parseDomains(text: String?): List<String> =
        text.orEmpty().split(',', ' ', '\n', '\r', '\t', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                if (xrayPrefixes.any { entry.startsWith(it, ignoreCase = true) }) {
                    entry
                } else {
                    val host = entry.substringAfter("://", entry)
                        .substringBefore('/').substringBefore('?').substringBefore('#')
                        .substringAfterLast('@').substringBefore(':')
                        .trim('.').lowercase()
                    host.takeIf { it.isNotEmpty() && !it.contains(' ') }?.let { "domain:$it" }
                }
            }
            .distinct()

    /** True when the chosen DNS is an ad-blocking preset: ads are then also blocked by routing. */
    fun adsRoutingBlock(vpnDns: String?, remoteDns: String?): Boolean =
        DnsPresets.detect(vpnDns, remoteDns)?.category == DnsPresets.Category.ADBLOCK

    /** With IPv6 off the core only asks for IPv4 addresses (no wait for unused AAAA answers). */
    fun queryStrategy(ipv6Enabled: Boolean): String? = if (ipv6Enabled) null else "UseIPv4"

    /**
     * "Faster DNS for SlowDNS": local DNS + FakeDNS for this connection only, when the switch is
     * on, the app is in VPN mode and the profile is SSH over SlowDNS.
     */
    fun slowDnsBoost(switchOn: Boolean, vpnMode: Boolean, slowDnsProfile: Boolean): Boolean =
        switchOn && vpnMode && slowDnsProfile
}

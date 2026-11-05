package com.v2ray.ang.ui.main

import com.v2ray.ang.core.ssh.SshMode
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType

/**
 * Protocol families shown by the Cassanova config and home screens. SlowDNS is an SSH mode but
 * gets its own family because users pick servers by it.
 */
enum class ProtocolFilter {
    ALL, SSH, XRAY, HY2, SLOWDNS;

    fun matches(profile: ProfileItem): Boolean = this == ALL || this == of(profile)

    companion object {
        /** The family of [profile]; never [ALL]. */
        fun of(profile: ProfileItem): ProtocolFilter = when (profile.configType) {
            EConfigType.SSH ->
                if (SshMode.from(profile.sshMode) == SshMode.SLOWDNS) SLOWDNS else SSH
            EConfigType.HYSTERIA2, EConfigType.HYSTERIA -> HY2
            else -> XRAY
        }
    }
}

/** Short text for the protocol badge of a config card. */
internal fun protocolBadge(profile: ProfileItem): String = when (profile.configType) {
    EConfigType.SSH -> "SSH"
    EConfigType.VLESS -> "VL"
    EConfigType.VMESS -> "VM"
    EConfigType.TROJAN -> "TR"
    EConfigType.HYSTERIA2, EConfigType.HYSTERIA -> "HY2"
    EConfigType.SHADOWSOCKS -> "SS"
    EConfigType.WIREGUARD -> "WG"
    EConfigType.SOCKS -> "SK"
    EConfigType.HTTP -> "HT"
    else -> profile.configType.name.take(2)
}

/**
 * Host with its last part hidden, so a screenshot of the config list does not reveal the full
 * server address: `a.b.example.com` → `a.b.example.***`, `10.1.2.3` → `10.1.2.***`.
 */
internal fun maskHost(host: String?): String {
    val value = host?.trim().orEmpty()
    if (value.isEmpty()) return ""
    if (value.contains(':')) {
        // IPv6: keep the first two groups.
        val groups = value.trim('[', ']').split(':').filter { it.isNotEmpty() }
        return groups.take(2).joinToString(":") + ":***"
    }
    val labels = value.split('.')
    if (labels.size < 2) return value.take(3) + "***"
    return labels.dropLast(1).joinToString(".") + ".***"
}

/** `masked-host : port`, or just the masked host when there is no port. */
internal fun maskedAddress(host: String?, port: String?): String {
    val masked = maskHost(host)
    val p = port?.trim().orEmpty()
    return when {
        masked.isEmpty() -> ""
        p.isEmpty() -> masked
        else -> "$masked : $p"
    }
}

/** Transport details after the protocol name, e.g. `ws · tls` (empty for SSH and Hysteria). */
internal fun transportParts(profile: ProfileItem): List<String> {
    if (profile.configType == EConfigType.SSH ||
        profile.configType == EConfigType.HYSTERIA2 ||
        profile.configType == EConfigType.HYSTERIA
    ) return emptyList()
    val parts = mutableListOf<String>()
    profile.network?.takeIf { it.isNotBlank() && !it.equals("tcp", ignoreCase = true) }
        ?.let { parts.add(it) }
    profile.security?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
        ?.let { parts.add(it) }
    return parts
}

/** Ping quality used to color the ping text and badge. */
internal enum class PingLevel { NONE, GOOD, SLOW, FAILED }

internal fun pingLevel(delayMillis: Long): PingLevel = when {
    delayMillis == 0L -> PingLevel.NONE
    delayMillis < 0L -> PingLevel.FAILED
    delayMillis <= SLOW_PING_MS -> PingLevel.GOOD
    else -> PingLevel.SLOW
}

private const val SLOW_PING_MS = 200L

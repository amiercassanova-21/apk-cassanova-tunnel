package com.v2ray.ang.core.ssh

/**
 * How the SSH connection reaches the server.
 * [key] is the value persisted in ProfileItem.sshMode, never rename it.
 */
enum class SshMode(val key: String) {
    DIRECT("direct"),
    PAYLOAD("payload"),
    SSL("ssl"),
    SSL_PAYLOAD("ssl_payload"),
    SLOWDNS("slowdns");

    val usesTls: Boolean get() = this == SSL || this == SSL_PAYLOAD
    val usesPayload: Boolean get() = this == PAYLOAD || this == SSL_PAYLOAD

    companion object {
        fun from(key: String?): SshMode = entries.firstOrNull { it.key == key } ?: DIRECT
    }
}

/** Everything the SSH engine needs; built from a profile by SshTunnelManager. */
data class SshSettings(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val mode: SshMode = SshMode.DIRECT,
    val payload: String = "",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val sni: String = "",
    val udpgwPort: Int = DEFAULT_UDPGW_PORT,
    val dnsResolver: String = "",
    val dnsNameserver: String = "",
    val dnsPublicKey: String = "",
    val dnsRecordType: String = DNS_TYPE_TXT,
) {
    /** Payload alternatives: blocks separated by a line holding only `---`. */
    val payloadOptions: List<String>
        get() {
            val parts = payload.split(Regex("\\r?\\n\\s*---\\s*\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            return if (parts.size <= 1) listOf(payload) else parts
        }

    /** SNI alternatives, comma separated. */
    val sniOptions: List<String>
        get() = sni.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(sni) }

    /** Remote proxy (bug host) alternatives, comma separated. */
    val proxyHostOptions: List<String>
        get() = proxyHost.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(proxyHost) }

    /** How many distinct variants rotation goes through (1 = no rotation). */
    val variantCount: Int
        get() = maxOf(payloadOptions.size, sniOptions.size, proxyHostOptions.size)

    /**
     * Settings for rotation step [index]: each list advances on its own, so with 3 payloads and
     * 2 SNIs the steps are (p1,s1) (p2,s2) (p3,s1) … Used when a connection attempt fails.
     */
    fun variant(index: Int): SshSettings {
        val i = if (index < 0) 0 else index
        return copy(
            payload = payloadOptions[i % payloadOptions.size],
            sni = sniOptions[i % sniOptions.size],
            proxyHost = proxyHostOptions[i % proxyHostOptions.size],
        )
    }

    companion object {
        const val DEFAULT_UDPGW_PORT = 7300
        const val DNS_TYPE_TXT = "txt"
        const val DNS_TYPE_NULL = "null"
    }
}

/** Receives engine log lines; the Android side forwards them to LogUtil. */
fun interface SshLogger {
    fun log(message: String)
}

enum class SshConfigError { MISSING_CREDENTIALS, MISSING_PAYLOAD, MISSING_SLOWDNS }

private val hexKey = Regex("^[0-9a-fA-F]{64}$")

/** Returns the first problem that would stop the tunnel from connecting, or null. */
fun SshSettings.validate(): SshConfigError? = when {
    username.isBlank() || password.isEmpty() -> SshConfigError.MISSING_CREDENTIALS
    mode.usesPayload && payload.isBlank() -> SshConfigError.MISSING_PAYLOAD
    mode == SshMode.SLOWDNS && (dnsResolver.isBlank() || dnsNameserver.isBlank() || !hexKey.matches(dnsPublicKey)) ->
        SshConfigError.MISSING_SLOWDNS

    else -> null
}

/**
 * Host and port a TCP ping of an SSH profile should reach: the payload proxy when one is set,
 * else the SSH server. Null for SlowDNS, which talks UDP DNS and has no TCP endpoint to time.
 */
fun sshTcpTarget(profile: com.v2ray.ang.dto.entities.ProfileItem): Pair<String, Int>? {
    if (SshMode.from(profile.sshMode) == SshMode.SLOWDNS) return null
    val proxyHost = profile.sshProxyHost?.trim().orEmpty()
    val proxyPort = profile.sshProxyPort?.trim()?.toIntOrNull()
    if (proxyHost.isNotEmpty() && proxyPort != null) return proxyHost to proxyPort
    val host = profile.server?.trim().orEmpty()
    val port = profile.serverPort?.trim()?.toIntOrNull() ?: 22
    return if (host.isEmpty()) null else host to port
}

package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

/**
 * Cassanova SSH link: `ssh://` + base64(JSON of [SshShareItem]), the same pattern as vmess://.
 * Field names are part of the link format: never rename them.
 */
object SshFmt {

    data class SshShareItem(
        var v: Int = 1,
        var ps: String? = null,       // remarks
        var add: String? = null,      // server
        var port: String? = null,
        var user: String? = null,
        var pass: String? = null,
        var mode: String? = null,     // SshMode.key
        var payload: String? = null,
        var proxy: String? = null,    // remote proxy host
        var proxyPort: String? = null,
        var sni: String? = null,
        var udpgw: String? = null,
        var dns: String? = null,      // resolver
        var ns: String? = null,       // nameserver
        var key: String? = null,      // dnstt public key
        var type: String? = null,     // txt / null
    )

    fun toUri(config: ProfileItem): String = Utils.encode(JsonUtil.toJson(toShareItem(config)))

    fun parse(str: String): ProfileItem? {
        val json = Utils.decode(str.trim().removePrefix(AppConfig.SSH))
        if (json.isBlank()) return null
        val item = JsonUtil.fromJsonSafe(json, SshShareItem::class.java) ?: return null
        return fromShareItem(item)
    }

    fun toShareItem(config: ProfileItem): SshShareItem = SshShareItem(
        ps = config.remarks,
        add = config.server,
        port = config.serverPort,
        user = config.username,
        pass = config.password,
        mode = config.sshMode,
        payload = config.sshPayload,
        proxy = config.sshProxyHost,
        proxyPort = config.sshProxyPort,
        sni = config.sni.nullIfBlank(),
        udpgw = config.sshUdpgwPort,
        dns = config.dnsResolver,
        ns = config.dnsNameserver,
        key = config.dnsPublicKey,
        type = config.dnsRecordType,
    )

    /** Null when the link lacks a server address. */
    fun fromShareItem(item: SshShareItem): ProfileItem? {
        if (item.add.isNullOrBlank()) return null
        return ProfileItem.create(EConfigType.SSH).apply {
            remarks = item.ps.orEmpty().ifBlank { item.add.orEmpty() }
            server = item.add
            serverPort = item.port.nullIfBlank() ?: "22"
            username = item.user
            password = item.pass
            sshMode = item.mode
            sshPayload = item.payload
            sshProxyHost = item.proxy
            sshProxyPort = item.proxyPort
            sni = item.sni
            sshUdpgwPort = item.udpgw
            dnsResolver = item.dns
            dnsNameserver = item.ns
            dnsPublicKey = item.key
            dnsRecordType = item.type
        }
    }
}

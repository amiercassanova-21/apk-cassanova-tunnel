package com.v2ray.ang.core.ssh

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.util.LogUtil
import java.io.File

/**
 * Owns the SSH tunnel of the running SSH profile inside the core daemon process.
 * The Xray core reaches it through a SOCKS outbound to 127.0.0.1:[AppConfig.PORT_SSH_SOCKS]
 * (see CoreOutboundBuilder). Started and stopped by CoreServiceManager together with the core.
 *
 * No socket protection is needed: CoreVpnService always keeps this app's own package outside
 * the VPN (addDisallowedApplication / not in the allowed list), so SSH and dnstt traffic goes
 * straight to the underlying network.
 */
object SshTunnelManager {

    // The in-app Logcat screen only shows AppConfig.TAG, so SSH lines use it with a prefix.
    private const val TAG = AppConfig.TAG
    private const val DNSTT_LIB = "libdnstt.so"

    private var tunnel: SshTunnel? = null

    /** Starts (or restarts) the tunnel when [profile] is an SSH profile; stops it otherwise. */
    @Synchronized
    fun startIfNeeded(context: Context, profile: ProfileItem) {
        stop()
        if (profile.configType != EConfigType.SSH) return

        val settings = toSettings(profile)
        val dnstt = File(context.applicationInfo.nativeLibraryDir, DNSTT_LIB).takeIf { it.exists() }
        LogUtil.i(TAG, "SSH: starting tunnel, mode=${settings.mode.key}")
        val t = SshTunnel(
            settings, AppConfig.PORT_SSH_SOCKS, dnstt,
            logger = { LogUtil.i(TAG, "SSH: $it") },
            onStatus = { status ->
                NotificationManager.updateSshStatus(
                    context.getString(
                        when (status) {
                            SshStatus.CONNECTING -> R.string.ssh_status_connecting
                            SshStatus.CONNECTED -> R.string.ssh_status_connected
                            SshStatus.RECONNECTING -> R.string.ssh_status_reconnecting
                        }
                    )
                )
            },
        )
        t.start()
        tunnel = t
    }

    @Synchronized
    fun stop() {
        tunnel?.stop()
        tunnel = null
        NotificationManager.updateSshStatus(null)
    }

    fun toSettings(profile: ProfileItem): SshSettings = SshSettings(
        host = profile.server.orEmpty().trim(),
        port = profile.serverPort?.trim()?.toIntOrNull() ?: 22,
        username = profile.username.orEmpty(),
        password = profile.password.orEmpty(),
        mode = SshMode.from(profile.sshMode),
        payload = profile.sshPayload.orEmpty(),
        proxyHost = profile.sshProxyHost.orEmpty().trim(),
        proxyPort = profile.sshProxyPort?.trim()?.toIntOrNull() ?: 0,
        sni = profile.sni.orEmpty().trim(),
        udpgwPort = profile.sshUdpgwPort?.trim()?.toIntOrNull() ?: SshSettings.DEFAULT_UDPGW_PORT,
        dnsResolver = profile.dnsResolver.orEmpty().trim(),
        dnsNameserver = profile.dnsNameserver.orEmpty().trim(),
        dnsPublicKey = profile.dnsPublicKey.orEmpty().trim(),
        dnsRecordType = profile.dnsRecordType ?: SshSettings.DNS_TYPE_TXT,
    )
}

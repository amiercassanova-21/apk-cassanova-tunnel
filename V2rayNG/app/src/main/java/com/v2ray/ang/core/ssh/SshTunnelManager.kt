package com.v2ray.ang.core.ssh

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.ConfigLockManager
import com.v2ray.ang.handler.ConnLog
import com.v2ray.ang.handler.MmkvManager
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

    private var tunnel: SshEngine? = null

    /** Starts (or restarts) the tunnel when [profile] is an SSH profile; stops it otherwise. */
    @Synchronized
    fun startIfNeeded(context: Context, profile: ProfileItem) {
        stop()
        if (profile.configType != EConfigType.SSH) return

        val settings = toSettings(ConfigLockManager.unlock(profile))
        LogUtil.i(TAG, "SSH: starting tunnel, mode=${settings.mode.key}")
        ConnLog.info(context.getString(R.string.connlog_ssh_start, settings.mode.key))
        val t = createEngine(
            context, settings, AppConfig.PORT_SSH_SOCKS,
            logger = { line ->
                LogUtil.i(TAG, "SSH: $line")
                when (engineLineLevel(line)) {
                    ConnLog.Kind.OK -> ConnLog.ok("SSH: $line")
                    ConnLog.Kind.WARN -> ConnLog.warn("SSH: $line")
                    ConnLog.Kind.ERROR -> ConnLog.error("SSH: $line")
                    else -> ConnLog.info("SSH: $line")
                }
            },
            onStatus = { status ->
                when (status) {
                    SshStatus.CONNECTING -> ConnLog.info(context.getString(R.string.connlog_ssh_connecting))
                    SshStatus.CONNECTED -> ConnLog.ok(context.getString(R.string.connlog_ssh_connected))
                    SshStatus.RECONNECTING -> ConnLog.warn(context.getString(R.string.connlog_ssh_reconnecting))
                }
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
            onBanner = { html -> ConnLog.banner(html) },
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

    /**
     * The SSH engine chosen in Settings: the native Go engine when it is packaged (default), else
     * the Java (JSch) engine. Both serve the same local SOCKS5 port.
     */
    fun createEngine(
        context: Context,
        settings: SshSettings,
        socksPort: Int,
        logger: SshLogger,
        onStatus: (SshStatus) -> Unit = {},
        onBanner: (String) -> Unit = {},
    ): SshEngine {
        val libDir = context.applicationInfo.nativeLibraryDir
        val dnstt = File(libDir, DNSTT_LIB).takeIf { it.exists() }
        val native = File(libDir, NativeSshTunnel.LIB_NAME).takeIf { it.exists() }
        val choice = MmkvManager.decodeSettingsString(AppConfig.PREF_SSH_ENGINE, AppConfig.SSH_ENGINE_NATIVE)
        return if (useNativeEngine(choice, native != null)) {
            NativeSshTunnel(native!!, settings, socksPort, dnstt, logger, onStatus, onBanner)
        } else {
            SshTunnel(settings, socksPort, dnstt, logger, onStatus, onBanner)
        }
    }

    /** Pure: how an engine line is colored in the connection log. */
    fun engineLineLevel(line: String): ConnLog.Kind {
        val l = line.lowercase()
        return when {
            "failed" in l || "error" in l || "unavailable" in l || "exited" in l -> ConnLog.Kind.ERROR
            "missed" in l || "reconnecting" in l || "disconnected" in l || "no reply" in l -> ConnLog.Kind.WARN
            "connected" in l || "tls ready" in l || "server reply: http/1.1 101" in l || "server reply: http/1.1 200" in l -> ConnLog.Kind.OK
            else -> ConnLog.Kind.INFO
        }
    }

    /** Pure: native unless Java was chosen or the native binary is missing. */
    fun useNativeEngine(choice: String?, nativeAvailable: Boolean): Boolean =
        nativeAvailable && choice != AppConfig.SSH_ENGINE_JAVA

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

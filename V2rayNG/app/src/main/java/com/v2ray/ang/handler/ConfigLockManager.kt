package com.v2ray.ang.handler

import android.util.Base64
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.ConfigLock
import com.v2ray.ang.util.LogUtil

/**
 * Locked configs: a share link sealed with the app's build-time key.
 *
 * Link: `cslock://` + base64url(ConfigLock.seal(original share link)).
 * Stored profile: only display fields in clear (see [redact]) plus [ProfileItem.lockedData]
 * (the sealed body). The full profile is rebuilt only where the core needs it ([unlock]),
 * so editors, share, export and backups never see the hidden fields.
 *
 * This raises the bar, it is not unbreakable: the key ships inside the APK.
 */
object ConfigLockManager {

    private val key: ByteArray? by lazy { ConfigLock.unmaskKey(BuildConfig.LOCK_KEY) }

    /** False in builds made without the signing secrets (no lock key). */
    val isAvailable: Boolean get() = key != null

    fun isLocked(profile: ProfileItem?): Boolean = profile?.lockedData != null

    /** Seals a share link into a `cslock://` link, null when locking is unavailable. */
    fun lockLink(shareLink: String): String? {
        val k = key ?: return null
        val sealed = ConfigLock.seal(k, shareLink.toByteArray(Charsets.UTF_8))
        return AppConfig.LOCKED + encode(sealed)
    }

    /** Parses a `cslock://` link into a redacted, locked profile. */
    fun parseLocked(str: String): ProfileItem? {
        val body = str.trim().removePrefix(AppConfig.LOCKED)
        val full = openBody(body) ?: return null
        return redact(full, body)
    }

    /**
     * Full profile for the core; returns [profile] unchanged when it is not locked.
     * Only the core processes (:daemon, :tasks, :bg) may open a locked config: in the UI process
     * (e.g. "Copy full configuration" of a group) the sealed fields stay hidden.
     */
    fun unlock(profile: ProfileItem): ProfileItem {
        val body = profile.lockedData ?: return profile
        if (isUiProcess) return profile
        val full = openBody(body) ?: run {
            LogUtil.w(AppConfig.TAG, "Locked profile could not be opened: ${profile.remarks}")
            return profile
        }
        full.remarks = profile.remarks
        full.subscriptionId = profile.subscriptionId
        full.addedTime = profile.addedTime
        return full
    }

    private val isUiProcess: Boolean by lazy {
        val name = try {
            java.io.File("/proc/self/cmdline").readText().trim { it <= ' ' }
        } catch (_: Exception) {
            ""
        }
        // Unknown name: assume UI so nothing leaks.
        name.isEmpty() || name == BuildConfig.APPLICATION_ID
    }

    private fun openBody(body: String): ProfileItem? {
        val k = key ?: return null
        val sealed = decode(body) ?: return null
        val link = ConfigLock.open(k, sealed)?.toString(Charsets.UTF_8) ?: return null
        if (link.startsWith(AppConfig.LOCKED)) return null
        return AngConfigManager.parseShareLink(link)
    }

    /** Keeps only what the list, groups and TCPing need; everything else stays sealed. */
    fun redact(full: ProfileItem, body: String): ProfileItem =
        ProfileItem.create(full.configType).apply {
            remarks = full.remarks
            server = full.server
            serverPort = full.serverPort
            network = full.network
            headerType = full.headerType
            security = full.security
            browserDialerMode = full.browserDialerMode
            lockedData = body
        }

    private fun encode(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decode(text: String): ByteArray? = try {
        Base64.decode(text, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    } catch (_: IllegalArgumentException) {
        null
    }
}

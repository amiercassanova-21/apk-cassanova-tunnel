package com.v2ray.ang.handler

import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    /**
     * Checks the Cassanova releases (tags `build-N`, all published as pre-releases) and reports an
     * update when the newest build number is above [BuildConfig.CASSANOVA_BUILD]. The pre-release
     * switch is kept for compatibility but every Cassanova build is a pre-release, so all count.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        val url = AppConfig.APP_API_URL

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        var response = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000
            )
        )
        if (response.isNullOrEmpty()) {
            val httpPort = SettingsManager.getHttpPort()
            response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                )
            )
                ?: throw IllegalStateException("Failed to get response")
        }

        val releases = JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)?.toList()
            ?: throw IllegalStateException("No release list")
        val latest = newestBuild(releases) ?: return@withContext CheckUpdateResult(hasUpdate = false)
        val latestNumber = buildNumber(latest.tagName) ?: 0
        LogUtil.i(AppConfig.TAG, "Latest Cassanova build: $latestNumber (current: ${BuildConfig.CASSANOVA_BUILD})")

        return@withContext if (isNewer(latestNumber, BuildConfig.CASSANOVA_BUILD)) {
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = latest.tagName,
                releaseNotes = latest.body,
                downloadUrl = downloadUrlFor(latest, Build.SUPPORTED_ABIS.toList())
                    ?: throw IllegalStateException("No compatible APK found"),
                isPreRelease = latest.prerelease
            )
        } else {
            CheckUpdateResult(hasUpdate = false)
        }
    }

    /** `build-12` → 12; null for any other tag. */
    fun buildNumber(tag: String): Int? {
        val t = tag.trim()
        return if (t.startsWith("build-")) t.removePrefix("build-").toIntOrNull() else null
    }

    /** The release with the highest build number, ignoring tags that are not `build-N`. */
    fun newestBuild(releases: List<GitHubRelease>): GitHubRelease? =
        releases.filter { buildNumber(it.tagName) != null }.maxByOrNull { buildNumber(it.tagName) ?: 0 }

    /** A local build without a number (0) never asks to update, so test builds are not nagged. */
    fun isNewer(latest: Int, current: Int): Boolean = current > 0 && latest > current

    /** The APK for the first supported ABI that has one, else the universal APK. */
    fun downloadUrlFor(release: GitHubRelease, abis: List<String>, lite: Boolean = Edition.isLite): String? {
        // One release carries both editions; Lite files are named AmierVPN_Lite_….
        val apks = release.assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) && it.name.contains("_Lite_", ignoreCase = true) == lite
        }
        abis.forEach { abi ->
            apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it.browserDownloadUrl }
        }
        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }?.browserDownloadUrl
    }
}

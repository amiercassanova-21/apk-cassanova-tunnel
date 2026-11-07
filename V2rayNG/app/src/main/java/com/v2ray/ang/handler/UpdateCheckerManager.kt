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
     * Checks the Cassanova releases (tags `build-N`) and reports an update when this edition's APK
     * in the newest release has a build number above [BuildConfig.CASSANOVA_BUILD]. The
     * pre-release switch is kept for compatibility; every Cassanova build counts.
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
        // Each app keeps its own build number: an app that did not change is re-published in
        // later releases with its old file (e.g. AmierVPN_Lite_1.1.0-b31 in build-33), so the
        // number comes from this edition's file, not from the release tag.
        val asset = assetFor(latest, Build.SUPPORTED_ABIS.toList())
            ?: return@withContext CheckUpdateResult(hasUpdate = false)
        val latestNumber = assetBuildNumber(asset.name) ?: buildNumber(latest.tagName) ?: 0
        LogUtil.i(AppConfig.TAG, "Latest Cassanova build: $latestNumber (current: ${BuildConfig.CASSANOVA_BUILD})")

        return@withContext if (isNewer(latestNumber, BuildConfig.CASSANOVA_BUILD)) {
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = "build-$latestNumber",
                releaseNotes = latest.body,
                downloadUrl = asset.browserDownloadUrl,
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

    /** `AmierVPN_Lite_1.1.0-b31_arm64-v8a.apk` → 31; null when the name has no `-bN_`. */
    fun assetBuildNumber(name: String): Int? =
        Regex("-b(\\d+)_").find(name)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * This edition's APK for the first supported ABI that has one, else its universal APK.
     * Amier VPN files are `AmierVPN_<version>…`, Lite files `AmierVPN_Lite_…`; other apps in the
     * same release (Amier Tether) never match.
     */
    fun assetFor(release: GitHubRelease, abis: List<String>, lite: Boolean = Edition.isLite): GitHubRelease.Asset? {
        val apks = release.assets.filter {
            val name = it.name
            name.endsWith(".apk", ignoreCase = true) && name.startsWith("AmierVPN_", ignoreCase = true) &&
                name.startsWith("AmierVPN_Lite_", ignoreCase = true) == lite
        }
        abis.forEach { abi ->
            apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
    }

    /** Download URL of [assetFor]. */
    fun downloadUrlFor(release: GitHubRelease, abis: List<String>, lite: Boolean = Edition.isLite): String? =
        assetFor(release, abis, lite)?.browserDownloadUrl
}

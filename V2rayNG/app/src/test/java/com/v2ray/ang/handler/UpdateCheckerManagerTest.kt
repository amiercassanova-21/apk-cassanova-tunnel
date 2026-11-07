package com.v2ray.ang.handler

import com.v2ray.ang.dto.GitHubRelease
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckerManagerTest {

    private fun release(tag: String, vararg apks: String) = GitHubRelease(
        tagName = tag,
        body = "",
        assets = apks.map { GitHubRelease.Asset(it, "https://example.com/$it") },
        prerelease = true
    )

    @Test
    fun buildNumberOnlyForBuildTags() {
        assertEquals(12, UpdateCheckerManager.buildNumber("build-12"))
        assertNull(UpdateCheckerManager.buildNumber("v2.3.15"))
        assertNull(UpdateCheckerManager.buildNumber("build-x"))
    }

    @Test
    fun newestBuildPicksHighestNumber() {
        val list = listOf(release("build-9"), release("v1.0"), release("build-11"), release("build-10"))
        assertEquals("build-11", UpdateCheckerManager.newestBuild(list)?.tagName)
        assertNull(UpdateCheckerManager.newestBuild(listOf(release("v1.0"))))
    }

    @Test
    fun newerOnlyWhenCurrentBuildIsKnown() {
        assertTrue(UpdateCheckerManager.isNewer(11, 10))
        assertFalse(UpdateCheckerManager.isNewer(10, 10))
        assertFalse(UpdateCheckerManager.isNewer(11, 0))
    }

    @Test
    fun downloadPrefersDeviceAbiThenUniversal() {
        val r = release("build-11", "AmierVPN_1.1.0-b11_arm64-v8a.apk", "AmierVPN_1.1.0-b11_armeabi-v7a.apk", "AmierVPN_1.1.0-b11_universal.apk")
        assertEquals("https://example.com/AmierVPN_1.1.0-b11_armeabi-v7a.apk", UpdateCheckerManager.downloadUrlFor(r, listOf("armeabi-v7a"), lite = false))
        assertEquals("https://example.com/AmierVPN_1.1.0-b11_universal.apk", UpdateCheckerManager.downloadUrlFor(r, listOf("x86_64"), lite = false))
        assertNull(UpdateCheckerManager.downloadUrlFor(release("build-1"), listOf("arm64-v8a")))
    }

    @Test
    fun eachEditionOnlyGetsItsOwnApk() {
        val r = release(
            "build-24",
            "AmierVPN_1.1.0-b24_arm64-v8a.apk", "AmierVPN_1.1.0-b24_armeabi-v7a.apk", "AmierVPN_1.1.0-b24_universal.apk",
            "AmierVPN_Lite_1.1.0-b24_arm64-v8a.apk", "AmierVPN_Lite_1.1.0-b24_armeabi-v7a.apk",
        )
        assertEquals(
            "https://example.com/AmierVPN_1.1.0-b24_arm64-v8a.apk",
            UpdateCheckerManager.downloadUrlFor(r, listOf("arm64-v8a"), lite = false)
        )
        assertEquals(
            "https://example.com/AmierVPN_Lite_1.1.0-b24_arm64-v8a.apk",
            UpdateCheckerManager.downloadUrlFor(r, listOf("arm64-v8a"), lite = true)
        )
        // Lite has no universal file: an unknown ABI gets nothing rather than the full edition.
        assertNull(UpdateCheckerManager.downloadUrlFor(r, listOf("x86_64"), lite = true))
    }

    @Test
    fun eachAppKeepsItsOwnBuildNumber() {
        // build-33 changed only Amier VPN: Lite (b31) and Tether (b28) were re-published as they were.
        val r = release(
            "build-33",
            "AmierVPN_1.1.0-b33_arm64-v8a.apk", "AmierVPN_1.1.0-b33_universal.apk",
            "AmierVPN_Lite_1.1.0-b31_arm64-v8a.apk",
            "AmierTether_1.0.0-b28_arm64-v8a.apk",
        )
        val full = UpdateCheckerManager.assetFor(r, listOf("arm64-v8a"), lite = false)!!
        val lite = UpdateCheckerManager.assetFor(r, listOf("arm64-v8a"), lite = true)!!
        assertEquals(33, UpdateCheckerManager.assetBuildNumber(full.name))
        assertEquals(31, UpdateCheckerManager.assetBuildNumber(lite.name))
        // Lite b31 installed: no update offered although the release is build-33.
        assertFalse(UpdateCheckerManager.isNewer(UpdateCheckerManager.assetBuildNumber(lite.name)!!, 31))
        assertTrue(UpdateCheckerManager.isNewer(UpdateCheckerManager.assetBuildNumber(full.name)!!, 31))
        assertNull(UpdateCheckerManager.assetBuildNumber("C_arm64-v8a.apk"))
    }

    @Test
    fun tetherFilesNeverMatchAmierVpn() {
        val r = release("build-30", "AmierTether_1.0.0-b30_arm64-v8a.apk", "AmierVPN_1.1.0-b30_armeabi-v7a.apk")
        assertEquals(
            "https://example.com/AmierVPN_1.1.0-b30_armeabi-v7a.apk",
            UpdateCheckerManager.downloadUrlFor(r, listOf("arm64-v8a", "armeabi-v7a"), lite = false)
        )
        assertNull(UpdateCheckerManager.assetFor(r, listOf("arm64-v8a"), lite = true))
    }
}

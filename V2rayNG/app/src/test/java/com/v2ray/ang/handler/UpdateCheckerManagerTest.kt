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
        val r = release("build-11", "C_arm64-v8a.apk", "C_armeabi-v7a.apk", "C_universal.apk")
        assertEquals("https://example.com/C_armeabi-v7a.apk", UpdateCheckerManager.downloadUrlFor(r, listOf("armeabi-v7a")))
        assertEquals("https://example.com/C_universal.apk", UpdateCheckerManager.downloadUrlFor(r, listOf("x86_64")))
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
}

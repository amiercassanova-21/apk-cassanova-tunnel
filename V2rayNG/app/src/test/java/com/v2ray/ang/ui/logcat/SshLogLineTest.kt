package com.v2ray.ang.ui.logcat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SshLogLineTest {
    @Test
    fun sshEngineLinesAreKept() {
        assertTrue(isSshLogLine("11-05 07:56:01.123 I/com.v2ray.ang( 123): SSH: SSH connected (SSH-2.0-dropbear)"))
        assertTrue(isSshLogLine("I/com.v2ray.ang( 9): SSH test: no session, mode=slowdns"))
        assertTrue(isSshLogLine("I/com.v2ray.ang( 9): SSH: SlowDNS restart failed"))
    }

    @Test
    fun otherLinesAreDropped() {
        assertFalse(isSshLogLine("I/GoLog( 1): [Info] xray started"))
        assertFalse(isSshLogLine(""))
    }
}

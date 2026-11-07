package com.v2ray.ang.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionTapsTest {

    @Test
    fun countdownShowsFromTheFourthTap() {
        var count = 0
        var last = 0L
        val hints = mutableListOf<Int?>()
        for (i in 1..7) {
            val now = 1_000L + i * 500
            count = VersionTaps.next(count, last, now)
            last = now
            hints.add(VersionTaps.remaining(count))
        }
        assertEquals(listOf(null, null, null, 3, 2, 1, null), hints)
        assertTrue(VersionTaps.switches(count))
    }

    @Test
    fun aLongPauseStartsOver() {
        assertEquals(1, VersionTaps.next(0, 0L, 5_000L))
        assertEquals(4, VersionTaps.next(3, 10_000L, 10_000L + VersionTaps.RESET_MS))
        assertEquals(1, VersionTaps.next(5, 10_000L, 10_000L + VersionTaps.RESET_MS + 1))
        assertFalse(VersionTaps.switches(6))
        assertNull(VersionTaps.remaining(0))
    }
}

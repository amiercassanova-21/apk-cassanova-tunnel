package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Calendar

class TrafficTrackerTest {

    @Test
    fun sameDayAddsUp() {
        assertEquals("2026-11-05" to 1500L, TrafficTracker.addToDay("2026-11-05", 1000L, "2026-11-05", 500L))
    }

    @Test
    fun newDayStartsFromZero() {
        assertEquals("2026-11-06" to 300L, TrafficTracker.addToDay("2026-11-05", 99999L, "2026-11-06", 300L))
        assertEquals("2026-11-06" to 300L, TrafficTracker.addToDay(null, 0L, "2026-11-06", 300L))
    }

    @Test
    fun negativeBytesAreIgnored() {
        assertEquals("2026-11-05" to 1000L, TrafficTracker.addToDay("2026-11-05", 1000L, "2026-11-05", -5L))
    }

    @Test
    fun dayKeyUsesLocalCalendarDate() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 5, 23, 59, 0) }
        assertEquals("2026-11-05", TrafficTracker.dayKey(c.time))
    }
}

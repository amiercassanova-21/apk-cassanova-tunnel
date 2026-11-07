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

    @Test
    fun speedHistoryKeepsTheNewestSamples() {
        var stored: String? = null
        for (i in 1..5) stored = TrafficTracker.appendHistory(stored, i * 10L, i.toLong(), max = 3)
        assertEquals("30:3,40:4,50:5", stored)
        assertEquals(listOf(30L to 3L, 40L to 4L, 50L to 5L), TrafficTracker.parseHistory(stored))
        assertEquals("0:0", TrafficTracker.appendHistory("", -5, -1))
    }

    @Test
    fun speedHistoryParseSkipsGarbage() {
        assertEquals(emptyList<Pair<Long, Long>>(), TrafficTracker.parseHistory(null))
        assertEquals(emptyList<Pair<Long, Long>>(), TrafficTracker.parseHistory(""))
        assertEquals(listOf(1L to 2L), TrafficTracker.parseHistory("x,1:2,3:,4:5:6"))
    }
}

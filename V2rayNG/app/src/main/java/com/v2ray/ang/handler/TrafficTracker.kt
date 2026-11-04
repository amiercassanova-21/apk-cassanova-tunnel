package com.v2ray.ang.handler

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cassanova home stats shared between the core daemon (writer) and the UI process (reader)
 * through the multi-process settings MMKV: today's data usage, current speeds and the
 * connected-since time.
 */
object TrafficTracker {
    private const val KEY_DAY = "cassanova_traffic_day"
    private const val KEY_DAY_BYTES = "cassanova_traffic_day_bytes"
    private const val KEY_SPEED_UP = "cassanova_speed_up"
    private const val KEY_SPEED_DOWN = "cassanova_speed_down"
    private const val KEY_CONNECTED_AT = "cassanova_connected_at"

    data class Snapshot(
        val todayBytes: Long,
        val upBytesPerSec: Long,
        val downBytesPerSec: Long,
        val connectedAtMillis: Long,
    )

    /** Daemon: adds bytes moved since the last query and stores the current speeds. */
    fun record(upBytes: Long, downBytes: Long, upSpeed: Long, downSpeed: Long, now: Date = Date()) {
        val (day, total) = addToDay(
            storedDay = MmkvManager.decodeSettingsString(KEY_DAY),
            storedBytes = MmkvManager.decodeSettingsLong(KEY_DAY_BYTES, 0L),
            today = dayKey(now),
            bytes = upBytes + downBytes,
        )
        MmkvManager.encodeSettings(KEY_DAY, day)
        MmkvManager.encodeSettings(KEY_DAY_BYTES, total)
        MmkvManager.encodeSettings(KEY_SPEED_UP, upSpeed)
        MmkvManager.encodeSettings(KEY_SPEED_DOWN, downSpeed)
    }

    /** Daemon: the core just started. */
    fun markConnected(now: Long = System.currentTimeMillis()) {
        MmkvManager.encodeSettings(KEY_CONNECTED_AT, now)
        MmkvManager.encodeSettings(KEY_SPEED_UP, 0L)
        MmkvManager.encodeSettings(KEY_SPEED_DOWN, 0L)
    }

    /** Daemon: the core stopped. */
    fun markDisconnected() {
        MmkvManager.encodeSettings(KEY_CONNECTED_AT, 0L)
        MmkvManager.encodeSettings(KEY_SPEED_UP, 0L)
        MmkvManager.encodeSettings(KEY_SPEED_DOWN, 0L)
    }

    /** UI: current values; today's bytes read as 0 once the date has changed. */
    fun snapshot(now: Date = Date()): Snapshot {
        val sameDay = MmkvManager.decodeSettingsString(KEY_DAY) == dayKey(now)
        return Snapshot(
            todayBytes = if (sameDay) MmkvManager.decodeSettingsLong(KEY_DAY_BYTES, 0L) else 0L,
            upBytesPerSec = MmkvManager.decodeSettingsLong(KEY_SPEED_UP, 0L),
            downBytesPerSec = MmkvManager.decodeSettingsLong(KEY_SPEED_DOWN, 0L),
            connectedAtMillis = MmkvManager.decodeSettingsLong(KEY_CONNECTED_AT, 0L),
        )
    }

    /** Pure: the day's running total, restarted when [today] differs from [storedDay]. */
    fun addToDay(storedDay: String?, storedBytes: Long, today: String, bytes: Long): Pair<String, Long> {
        val base = if (storedDay == today) storedBytes else 0L
        return today to (base + bytes.coerceAtLeast(0L))
    }

    fun dayKey(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
}

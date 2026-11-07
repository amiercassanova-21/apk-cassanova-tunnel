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
    private const val KEY_LAST_PING = "cassanova_last_ping"
    private const val KEY_SPEED_HISTORY = "cassanova_speed_history"
    const val SPEED_HISTORY_MAX = 60

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
        // Kept by the daemon so the home graph survives the UI process being swiped away.
        MmkvManager.encodeSettings(KEY_SPEED_HISTORY, appendHistory(MmkvManager.decodeSettingsString(KEY_SPEED_HISTORY), downSpeed, upSpeed))
    }

    /** UI: the last [SPEED_HISTORY_MAX] (down, up) speeds of this connection, oldest first. */
    fun speedHistory(): List<Pair<Long, Long>> = parseHistory(MmkvManager.decodeSettingsString(KEY_SPEED_HISTORY))

    /** Daemon: the core just started. */
    fun markConnected(now: Long = System.currentTimeMillis()) {
        MmkvManager.encodeSettings(KEY_LAST_PING, 0L)
        MmkvManager.encodeSettings(KEY_CONNECTED_AT, now)
        MmkvManager.encodeSettings(KEY_SPEED_UP, 0L)
        MmkvManager.encodeSettings(KEY_SPEED_DOWN, 0L)
        MmkvManager.encodeSettings(KEY_SPEED_HISTORY, "")
    }

    /** UI: latest real-delay result of the running server (-1 failed); read by the widget. */
    fun recordPing(delayMillis: Long) {
        MmkvManager.encodeSettings(KEY_LAST_PING, delayMillis)
    }

    /** Connected-since time written by the daemon, 0 when not connected. */
    fun connectedAt(): Long = MmkvManager.decodeSettingsLong(KEY_CONNECTED_AT, 0L)

    /** Latest ping, 0 when none since the connection started. */
    fun lastPing(): Long = MmkvManager.decodeSettingsLong(KEY_LAST_PING, 0L)

    /** Daemon: the core stopped. */
    fun markDisconnected() {
        MmkvManager.encodeSettings(KEY_LAST_PING, 0L)
        MmkvManager.encodeSettings(KEY_CONNECTED_AT, 0L)
        MmkvManager.encodeSettings(KEY_SPEED_UP, 0L)
        MmkvManager.encodeSettings(KEY_SPEED_DOWN, 0L)
        MmkvManager.encodeSettings(KEY_SPEED_HISTORY, "")
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

    /** Pure: "down:up" samples joined by commas, the oldest dropped beyond [max]. */
    fun appendHistory(stored: String?, down: Long, up: Long, max: Int = SPEED_HISTORY_MAX): String =
        (stored.orEmpty().split(',').filter { it.isNotEmpty() } + "${down.coerceAtLeast(0L)}:${up.coerceAtLeast(0L)}")
            .takeLast(max)
            .joinToString(",")

    /** Pure: reads [appendHistory] output; malformed samples are skipped. */
    fun parseHistory(stored: String?): List<Pair<Long, Long>> =
        stored.orEmpty().split(',').mapNotNull { sample ->
            val parts = sample.split(':')
            val down = parts.getOrNull(0)?.toLongOrNull()
            val up = parts.getOrNull(1)?.toLongOrNull()
            if (parts.size == 2 && down != null && up != null) down to up else null
        }

    fun dayKey(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
}

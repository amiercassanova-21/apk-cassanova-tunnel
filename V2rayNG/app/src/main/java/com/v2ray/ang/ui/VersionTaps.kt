package com.v2ray.ang.ui

/**
 * Cassanova: the hidden admin switch in About (tap the version line [TAPS] times). Like the
 * Android developer options, a countdown shows from tap [HINT_FROM]; a pause longer than
 * [RESET_MS] starts the count again.
 */
internal object VersionTaps {
    const val TAPS = 7
    const val HINT_FROM = 4
    const val RESET_MS = 3_000L

    /** The tap count after a tap at [now]; [lastTapAt] is 0 before the first tap. */
    fun next(count: Int, lastTapAt: Long, now: Long): Int =
        if (lastTapAt > 0 && now - lastTapAt > RESET_MS) 1 else count + 1

    /** Taps still needed to switch, or null when no countdown is shown for [count]. */
    fun remaining(count: Int): Int? = if (count in HINT_FROM until TAPS) TAPS - count else null

    fun switches(count: Int): Boolean = count >= TAPS
}

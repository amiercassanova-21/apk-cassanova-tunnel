package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InternetSpeedTestTest {

    private val second = 1_000_000_000L

    @Test
    fun mbpsFromBytesAndTime() {
        // 1.25 MB in 1 s = 10 Mbps.
        assertEquals(10.0, InternetSpeedTest.mbps(1_250_000, second), 1e-9)
        assertEquals(0.0, InternetSpeedTest.mbps(1_250_000, 0), 1e-9)
        assertEquals(0.0, InternetSpeedTest.mbps(0, second), 1e-9)
    }

    @Test
    fun phaseRateLeavesOutWarmUp() {
        // 5 MB in the first 2 s (ramp-up / buffers), then 10 MB in the next 8 s = 10 Mbps.
        val rate = InternetSpeedTest.phaseRate(15_000_000, 10 * second, 5_000_000, 2 * second)
        assertEquals(10.0, rate, 1e-9)
        // Upload that stalls after the buffers fill: nothing after warm-up = 0 Mbps.
        assertEquals(0.0, InternetSpeedTest.phaseRate(3_000_000, 10 * second, 3_000_000, 2 * second), 1e-9)
    }

    @Test
    fun phaseRateWithoutWarmUpUsesWholePhase() {
        // Cap reached before the warm-up mark.
        assertEquals(8.0, InternetSpeedTest.phaseRate(1_000_000, second, null, null), 1e-9)
        assertEquals(8.0, InternetSpeedTest.phaseRate(1_000_000, second, 900_000, second), 1e-9)
    }

    @Test
    fun medianPing() {
        assertEquals(41L, InternetSpeedTest.median(listOf(90, 40, 30, 45, 41)))
        assertNull(InternetSpeedTest.median(emptyList()))
    }
}

package com.v2ray.ang.ui.main

import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.effectiveUiStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeA3DTest {

    @Test
    fun speedHistoryKeepsNewestSixty() {
        var h = emptyList<SpeedSample>()
        repeat(65) { h = appendSpeedSample(h, SpeedSample(it.toLong(), 0)) }
        assertEquals(SPEED_HISTORY_SIZE, h.size)
        assertEquals(5L, h.first().down)
        assertEquals(64L, h.last().down)
    }

    @Test
    fun sparklineTopIsAtLeastOneKilobyte() {
        assertEquals(1024L, sparklineMax(emptyList()))
        assertEquals(1024L, sparklineMax(listOf(SpeedSample(10, 20))))
        assertEquals(5000L, sparklineMax(listOf(SpeedSample(10, 5000), SpeedSample(4000, 1))))
    }

    @Test
    fun tiltIsNeutralWhenUprightAndLimited() {
        val (rx, ry) = tiltFromGravity(0f, 9.80665f * 0.75f, 8f)
        assertEquals(0f, rx, 0.01f)
        assertEquals(0f, ry, 0.01f)
        val (bx, by) = tiltFromGravity(-50f, 50f, 8f)
        assertTrue(bx <= 8f && by <= 8f && bx >= -8f && by >= -8f)
    }

    @Test
    fun styleIsAUnlessExperimentalIsUnlocked() {
        assertEquals(ThemeManager.STYLE_A, effectiveUiStyle(ThemeManager.STYLE_C, false))
        assertEquals(ThemeManager.STYLE_C, effectiveUiStyle(ThemeManager.STYLE_C, true))
        assertEquals(ThemeManager.STYLE_A, effectiveUiStyle(null, true))
        assertEquals(ThemeManager.STYLE_A, effectiveUiStyle("x", true))
    }
}

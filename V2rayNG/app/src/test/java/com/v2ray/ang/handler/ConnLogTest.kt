package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConnLogTest {

    @Test
    fun lineRoundTripKeepsMultiLineText() {
        val line = ConnLog.Line(1730000000000, ConnLog.Kind.BANNER, "<b>A</b>\nB\tC\\D\r")
        assertEquals(line, ConnLog.decode(ConnLog.encode(line)))
    }

    @Test
    fun badLinesAreSkipped() {
        assertNull(ConnLog.decode(""))
        assertNull(ConnLog.decode("x\tI\ttext"))
        assertNull(ConnLog.decode("1\tZ\ttext"))
        assertNull(ConnLog.decode("1\tItext"))
    }

    @Test
    fun bannerBelongsToTheLatestSession() {
        val lines = listOf(
            ConnLog.Line(1, ConnLog.Kind.SESSION, ""),
            ConnLog.Line(2, ConnLog.Kind.BANNER, "old"),
            ConnLog.Line(10, ConnLog.Kind.SESSION, ""),
            ConnLog.Line(11, ConnLog.Kind.INFO, "x"),
        )
        assertNull(ConnLog.sessionBanner(lines))
        assertEquals(10L to "new", ConnLog.sessionBanner(lines + ConnLog.Line(12, ConnLog.Kind.BANNER, "new")))
    }

    @Test
    fun unescapeLeavesUnknownSequences() {
        assertEquals("a\\qb", ConnLog.unescape("a\\qb"))
        assertEquals("line1\nline2", ConnLog.unescape("line1\\nline2"))
    }
}

package com.v2ray.ang.ui.connlog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannerTextTest {
    @Test
    fun htmlDetection() {
        assertTrue(looksLikeHtml("<font color=\"red\">CASSANOVA</font>"))
        assertTrue(looksLikeHtml("a<br>b"))
        assertFalse(looksLikeHtml("Masa aktif 30 hari\n1 < 2"))
    }

    @Test
    fun plainCopyDropsTags() {
        assertEquals("CASSANOVA\nTUNNELING & co", htmlToPlain("<b>CASSANOVA</b><br/>TUNNELING &amp; co"))
    }
}

package com.ljyh.mei.utils.lyric.edit

import com.ljyh.mei.ui.model.LyricSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricFormatDetectorTest {

    @Test
    fun detectsLrc() {
        assertEquals(
            LyricTextFormat.LRC,
            LyricFormatDetector.detect("[00:12.00]hello\n[00:14.00]world"),
        )
    }

    @Test
    fun detectsYrcWordTags() {
        assertEquals(
            LyricTextFormat.YRC,
            LyricFormatDetector.detect("[1000,3000](0,500,0)He(500,500,0)llo"),
        )
        assertTrue(LyricFormatDetector.hasWordTiming("[1000,3000](0,500,0)He(500,500,0)llo"))
    }

    @Test
    fun detectsQrcWordTags() {
        assertEquals(
            LyricTextFormat.QRC,
            LyricFormatDetector.detect("[1000,3000]He(1000,500)llo(1500,500)"),
        )
        assertTrue(LyricFormatDetector.hasWordTiming("[1000,3000]He(1000,500)llo(1500,500)"))
    }

    @Test
    fun detectsPlainText() {
        assertEquals(LyricTextFormat.PLAIN, LyricFormatDetector.detect("just lyrics\nno timestamps"))
        assertFalse(LyricFormatDetector.hasWordTiming("just lyrics"))
    }

    @Test
    fun detectsEnhancedLrc() {
        val sample = "[03:33.62]<03:33.62>是<03:33.80>怎<03:34.13>么"
        assertEquals(LyricTextFormat.ENHANCED_LRC, LyricFormatDetector.detect(sample))
        assertTrue(LyricFormatDetector.hasWordTiming(sample))

        val data = LyricFormatDetector.parseForDisplay(sample, null, 10_000L)
        assertNotNull(data)
        assertTrue(data!!.isVerbatim)
        assertEquals(LyricSource.Custom, data.source)
        assertEquals(1, data.lyricLine.lines.size)
    }

    @Test
    fun detectsTtml() {
        val ttml = "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body/></tt>"
        assertEquals(LyricTextFormat.TTML, LyricFormatDetector.detect(ttml))
    }

    @Test
    fun collapsedTimelineIsNotUsable() {
        val collapsed = "[00:01.00]a\n[00:01.00]b\n[00:01.00]c"
        assertFalse(LyricFormatDetector.isUsableTimeline(collapsed))
        assertTrue(LyricFormatDetector.isUsableTimeline("[00:01.00]a\n[00:02.00]b"))
    }

    @Test
    fun parseForDisplayKeepsRawTextAndCustomSource() {
        val raw = "[00:01.00]hello\n[00:02.00]world"
        val data = LyricFormatDetector.parseForDisplay(raw, null, 5_000L)
        assertNotNull(data)
        assertEquals(LyricSource.Custom, data!!.source)
        assertEquals(raw, data.rawLyrics)
        assertEquals(2, data.lyricLine.lines.size)
    }

    @Test
    fun parseForDisplayConvertsPlainTextToPseudoTimeline() {
        val data = LyricFormatDetector.parseForDisplay("first\nsecond", null, 8_000L)
        assertNotNull(data)
        assertEquals(2, data!!.lyricLine.lines.size)
        assertFalse(data.isVerbatim)
    }

    @Test
    fun parseForDisplayRejectsBlankInput() {
        assertNull(LyricFormatDetector.parseForDisplay("   ", null, 0L))
    }
}

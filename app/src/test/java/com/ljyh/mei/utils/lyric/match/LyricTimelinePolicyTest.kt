package com.ljyh.mei.utils.lyric.match

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricTimelinePolicyTest {

    @Test
    fun threeLinesWithTheSameTimestampAreCollapsed() {
        val text = "[00:01.00]a\n[00:01.00]b\n[00:01.00]c"
        assertTrue(LyricTimelinePolicy.hasCollapsedTimedLyricTimeline(text))
    }

    @Test
    fun distinctTimestampsAreUsable() {
        val text = "[00:01.00]a\n[00:02.00]b\n[00:03.00]c"
        assertFalse(LyricTimelinePolicy.hasCollapsedTimedLyricTimeline(text))
    }

    @Test
    fun shortTextIsNeverCollapsed() {
        val text = "[00:01.00]a\n[00:01.00]b"
        assertFalse(LyricTimelinePolicy.hasCollapsedTimedLyricTimeline(text))
    }

    @Test
    fun usableTimelineRequiresTimestamps() {
        assertTrue(LyricTimelinePolicy.isUsableTimedLyricTimeline("[00:01.00]a\n[00:02.00]b"))
        assertFalse(LyricTimelinePolicy.isUsableTimedLyricTimeline("no timestamps at all"))
    }
}

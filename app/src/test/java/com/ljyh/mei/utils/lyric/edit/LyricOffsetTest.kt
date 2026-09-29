package com.ljyh.mei.utils.lyric.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricOffsetTest {

    @Test
    fun snapsToTheFiftyMillisecondStep() {
        assertEquals(150L, normalizeLyricOffsetMs(160L))
        assertEquals(200L, normalizeLyricOffsetMs(180L))
        assertEquals(0L, normalizeLyricOffsetMs(20L))
    }

    @Test
    fun clampsToTheSupportedRange() {
        assertEquals(MAX_LYRIC_OFFSET_MS, normalizeLyricOffsetMs(9_999L))
        assertEquals(MIN_LYRIC_OFFSET_MS, normalizeLyricOffsetMs(-9_999L))
    }

    @Test
    fun keepsAlreadyNormalizedValues() {
        assertEquals(-500L, normalizeLyricOffsetMs(-500L))
        assertEquals(5_000L, normalizeLyricOffsetMs(5_000L))
    }
}

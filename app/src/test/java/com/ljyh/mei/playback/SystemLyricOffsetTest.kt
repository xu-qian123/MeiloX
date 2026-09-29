package com.ljyh.mei.playback

import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.ui.model.LyricData
import com.ljyh.mei.ui.model.LyricSource
import com.ljyh.mei.utils.lyric.LRCParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * System lyric publication shifts line timestamps by the inverse of the
 * per-song offset, so consumers highlighting at the real playback position
 * stay in sync with the UI.
 */
class SystemLyricOffsetTest {

    private fun metadata(durationMs: Long = 10_000L) = MediaMetadata(
        id = 1L,
        title = "title",
        coverUrl = "",
        artists = emptyList(),
        duration = durationMs,
        album = MediaMetadata.Album(id = 2L, title = "album"),
    )

    private fun lyricData(): LyricData = LyricData(
        source = LyricSource.NetEaseCloudMusic,
        lyricLine = LRCParser.parse("[00:01.00]first\n[00:04.00]second", null),
    )

    @Test
    fun positiveOffsetShiftsLinesEarlierForConsumers() {
        val song = metadata().toSystemLyricSong(lyricData(), offsetMs = 500L)
        val lines = song.lyrics.orEmpty()
        assertEquals(2, lines.size)
        // 1000 ms line start − 500 ms shift.
        assertEquals(500L, lines[0].begin)
        assertEquals(3_500L, lines[1].begin)
    }

    @Test
    fun negativeOffsetShiftsLinesLaterForConsumers() {
        val song = metadata().toSystemLyricSong(lyricData(), offsetMs = -1_000L)
        val lines = song.lyrics.orEmpty()
        assertEquals(2_000L, lines[0].begin)
        assertEquals(5_000L, lines[1].begin)
    }

    @Test
    fun shiftedLinesNeverStartBelowZero() {
        val song = metadata().toSystemLyricSong(lyricData(), offsetMs = 5_000L)
        val lines = song.lyrics.orEmpty()
        assertEquals(0L, lines[0].begin)
        assertTrue(lines.all { it.end > it.begin })
    }

    @Test
    fun zeroOffsetKeepsOriginalTiming() {
        val song = metadata().toSystemLyricSong(lyricData(), offsetMs = 0L)
        assertEquals(1_000L, song.lyrics.orEmpty()[0].begin)
    }

    @Test
    fun emptySourcePublishesNoLines() {
        val data = LyricData(source = LyricSource.Empty, lyricLine = lyricData().lyricLine)
        assertTrue(metadata().toSystemLyricSong(data, offsetMs = 500L).lyrics.orEmpty().isEmpty())
    }
}

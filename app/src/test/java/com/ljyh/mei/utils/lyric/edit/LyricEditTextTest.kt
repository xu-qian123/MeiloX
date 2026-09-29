package com.ljyh.mei.utils.lyric.edit

import com.ljyh.mei.ui.model.LyricData
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricEditTextTest {

    @Test
    fun pseudoLrcDistributesTimestampsOverDuration() {
        val lrc = plainLyricsToPseudoLrc("a\nb\nc", 9_000L)
        val lines = lrc.lines()
        assertEquals(3, lines.size)
        assertEquals("[00:00.000]a", lines[0])
        assertEquals("[00:03.000]b", lines[1])
        assertEquals("[00:06.000]c", lines[2])
    }

    @Test
    fun pseudoLrcFallsBackToFixedDuration() {
        val lines = plainLyricsToPseudoLrc("a\nb", 0L)
        assertEquals(2, lines.lines().size)
        assertTrue(lines.lines()[1].startsWith("[00:04.000]"))
    }

    @Test
    fun editableTextPrefersRawLyrics() {
        val data = LyricData(
            lyricLine = SyncedLyrics(emptyList()),
            rawLyrics = "[00:01:00]legacy",
        )
        // Legacy timestamps are normalized before showing them in the editor.
        assertEquals("[00:01.00]legacy", toEditableLyricsText(data))
    }

    @Test
    fun editableTextSerializesParsedLinesAsFallback() {
        val data = LyricFormatDetector.parseForDisplay(
            "[00:01.00]hello\n[00:02.00]world",
            null,
            5_000L,
        )!!.copy(rawLyrics = null)
        val text = toEditableLyricsText(data)
        assertTrue(text.contains("hello"))
        assertTrue(text.contains("world"))
    }

    @Test
    fun editorInitialPrefersCustomLyric() {
        val data = LyricData(
            lyricLine = SyncedLyrics(emptyList()),
            rawLyrics = "network",
        )
        assertEquals("custom", resolveLyricsEditorInitialText("custom", data))
        assertEquals("network", resolveLyricsEditorInitialText(null, data))
    }
}

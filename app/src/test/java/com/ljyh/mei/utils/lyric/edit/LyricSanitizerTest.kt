package com.ljyh.mei.utils.lyric.edit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSanitizerTest {

    @Test
    fun removesCreditLinesAndKeepsRealLyrics() {
        val raw = """
            [00:00.00]作词: 张三
            [00:02.00]作曲: 李四
            [00:05.00]Hello world
            [00:07.00]Goodbye moon
        """.trimIndent()
        val result = sanitizeMatchedLyrics(raw, null, "Hello", "王五")
        assertFalse(result.lyrics.contains("作词"))
        assertFalse(result.lyrics.contains("作曲"))
        assertTrue(result.lyrics.contains("Hello world"))
        assertTrue(result.lyrics.contains("Goodbye moon"))
    }

    @Test
    fun removesEnglishCredits() {
        val raw = """
            [00:00.00]Lyrics by Someone
            [00:05.00]Real line
        """.trimIndent()
        val result = sanitizeMatchedLyrics(raw, null, "Real", "Someone")
        assertFalse(result.lyrics.contains("Lyrics by"))
        assertTrue(result.lyrics.contains("Real line"))
    }

    @Test
    fun keepsEverythingWhenAllLinesAreMetadata() {
        val raw = "[ar:Artist]\n[ti:Title]\n[by:Someone]"
        val result = sanitizeMatchedLyrics(raw, null, "Title", "Artist")
        // Cleaning everything away would break the timeline, so the original wins.
        assertTrue(result.lyrics.isNotBlank())
    }

    @Test
    fun ttmlIsReturnedUntouched() {
        val ttml = "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div><p>hi</p></div></body></tt>"
        val result = sanitizeMatchedLyrics(ttml, null, "hi", "artist")
        assertTrue(result.lyrics == ttml)
    }
}

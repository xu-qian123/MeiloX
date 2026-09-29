package com.ljyh.mei.utils.lyric

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedLRCParserTest {

    /** Real-world sample reported by the user. */
    private val sample =
        "[03:33.62]<03:33.62>是<03:33.80>怎<03:34.13>么<03:34.40>得<03:34.60>到<03:34.78>的<03:34.99>快<03:35.50>乐"

    @Test
    fun parsesWordTagsIntoKaraokeSyllables() {
        val lines = EnhancedLRCParser.parse(sample, null).lines
        assertEquals(1, lines.size)

        val line = lines.first() as KaraokeLine
        assertEquals(8, line.syllables.size)
        assertEquals(3 * 60_000 + 33_000 + 620, line.syllables.first().start)
        assertEquals(3 * 60_000 + 33_000 + 800, line.syllables.first().end)
        assertEquals("是", line.syllables.first().content)
        assertEquals("乐", line.syllables.last().content)
        assertEquals(line.syllables.first().start, line.start)
    }

    @Test
    fun lastSyllableEndsAtNextLineStart() {
        val text = "[00:01.00]<00:01.00>a<00:01.50>b\n[00:03.00]<00:03.00>c"
        val lines = EnhancedLRCParser.parse(text, null).lines
        assertEquals(2, lines.size)
        assertEquals(3_000, lines[0].end)
        assertEquals(3_000, (lines[0] as KaraokeLine).syllables.last().end)
    }

    @Test
    fun lastSyllableGetsFallbackDurationWithoutNextLine() {
        val lines = EnhancedLRCParser.parse("[00:10.00]<00:10.00>solo", null).lines
        val syllable = (lines.first() as KaraokeLine).syllables.last()
        assertTrue(syllable.end > syllable.start)
    }

    @Test
    fun mergesExternalTranslation() {
        val main = "[00:01.00]<00:01.00>Hello<00:01.50>world"
        val translation = "[00:01.00]你好 世界"
        val lines = EnhancedLRCParser.parse(main, translation).lines
        assertEquals("你好 世界", (lines.first() as KaraokeLine).translation)
    }

    @Test
    fun plainLineInsideEnhancedFileBecomesSingleSyllable() {
        val text = "[00:01.00]<00:01.00>a\n[00:02.00]plain line"
        val lines = EnhancedLRCParser.parse(text, null).lines
        assertEquals(2, lines.size)
        val plain = lines[1] as KaraokeLine
        assertEquals(1, plain.syllables.size)
        assertEquals("plain line", plain.syllables.first().content)
    }

    @Test
    fun canParseDetectsTheFormat() {
        assertTrue(EnhancedLRCParser.canParse(sample))
    }
}

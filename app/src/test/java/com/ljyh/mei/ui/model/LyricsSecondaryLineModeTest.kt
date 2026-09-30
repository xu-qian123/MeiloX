package com.ljyh.mei.ui.model

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.ljyh.mei.utils.lyric.LRCParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSecondaryLineModeTest {
    private val mainLrc = """
        [00:01.00]夜に駆ける
        [00:05.00]沈むように溶けてゆく
    """.trimIndent()

    private val translation = """
        [00:01.00]奔向夜晚
        [00:05.00]像沉没般融化
    """.trimIndent()

    private val roma = """
        [00:01.00]yoru ni kakeru
        [00:05.00]shizumu you ni tokete yuku
    """.trimIndent()

    @Test
    fun `resolve prefers phonetic when auto and both are available`() {
        assertEquals(
            LyricsSecondaryLineMode.PHONETIC,
            resolveLyricsSecondaryLineMode(
                requestedMode = null,
                translationEnabled = true,
                romanizationEnabled = true,
                hasTranslation = true,
                hasPhonetic = true,
            ),
        )
        assertEquals(
            LyricsSecondaryLineMode.TRANSLATION,
            resolveLyricsSecondaryLineMode(
                requestedMode = null,
                translationEnabled = true,
                romanizationEnabled = false,
                hasTranslation = true,
                hasPhonetic = true,
            ),
        )
        assertEquals(
            LyricsSecondaryLineMode.NONE,
            resolveLyricsSecondaryLineMode(
                requestedMode = null,
                translationEnabled = false,
                romanizationEnabled = false,
                hasTranslation = true,
                hasPhonetic = true,
            ),
        )
    }

    @Test
    fun `an explicit mode falls back to whatever the song actually has`() {
        // 在别的歌上选了音译，切到只有翻译的歌 → 自动退回翻译（不再整行空掉）。
        assertEquals(
            LyricsSecondaryLineMode.TRANSLATION,
            resolveLyricsSecondaryLineMode(
                requestedMode = LyricsSecondaryLineMode.PHONETIC,
                translationEnabled = true,
                romanizationEnabled = true,
                hasTranslation = true,
                hasPhonetic = false,
            ),
        )
        // 反之亦然。
        assertEquals(
            LyricsSecondaryLineMode.PHONETIC,
            resolveLyricsSecondaryLineMode(
                requestedMode = LyricsSecondaryLineMode.TRANSLATION,
                translationEnabled = true,
                romanizationEnabled = true,
                hasTranslation = false,
                hasPhonetic = true,
            ),
        )
        // 显式关闭则始终为空。
        assertEquals(
            LyricsSecondaryLineMode.NONE,
            resolveLyricsSecondaryLineMode(
                requestedMode = LyricsSecondaryLineMode.NONE,
                translationEnabled = true,
                romanizationEnabled = true,
                hasTranslation = true,
                hasPhonetic = true,
            ),
        )
    }

    @Test
    fun `cycle only walks through available modes plus off`() {
        assertEquals(
            LyricsSecondaryLineMode.PHONETIC,
            nextLyricsSecondaryLineMode(
                LyricsSecondaryLineMode.TRANSLATION,
                hasTranslation = true,
                hasPhonetic = true,
            ),
        )
        assertEquals(
            LyricsSecondaryLineMode.NONE,
            nextLyricsSecondaryLineMode(
                LyricsSecondaryLineMode.PHONETIC,
                hasTranslation = true,
                hasPhonetic = true,
            ),
        )
        assertEquals(
            LyricsSecondaryLineMode.TRANSLATION,
            nextLyricsSecondaryLineMode(
                LyricsSecondaryLineMode.NONE,
                hasTranslation = true,
                hasPhonetic = true,
            ),
        )
        // 只有翻译时不会循环到音译。
        assertEquals(
            LyricsSecondaryLineMode.NONE,
            nextLyricsSecondaryLineMode(
                LyricsSecondaryLineMode.TRANSLATION,
                hasTranslation = true,
                hasPhonetic = false,
            ),
        )
        assertEquals(
            LyricsSecondaryLineMode.NONE,
            nextLyricsSecondaryLineMode(
                LyricsSecondaryLineMode.NONE,
                hasTranslation = false,
                hasPhonetic = false,
            ),
        )
    }

    @Test
    fun `phonetic mode puts the romanization into the secondary line slot`() {
        val lyrics = LRCParser.parse(mainLrc, translation, roma)
        val phoneticLines = lyrics.withSecondaryLineMode(LyricsSecondaryLineMode.PHONETIC)

        val first = phoneticLines.lines[0] as KaraokeLine.MainKaraokeLine
        assertEquals("yoru ni kakeru", first.translation)
        // 行级 phonetic 要清掉，否则库的视图会把音译渲染两遍。
        assertNull(first.phonetic)
    }

    @Test
    fun `translation mode hides the phonetic rows`() {
        val lyrics = LRCParser.parse(mainLrc, translation, roma)
        val translationLines = lyrics.withSecondaryLineMode(LyricsSecondaryLineMode.TRANSLATION)

        val first = translationLines.lines[0] as KaraokeLine.MainKaraokeLine
        assertEquals("奔向夜晚", first.translation)
        assertNull(first.phonetic)
    }

    @Test
    fun `syllable level phonetics are cleared outside phonetic mode`() {
        val line = KaraokeLine.MainKaraokeLine(
            syllables = listOf(KaraokeSyllable("夜", 1_000, 2_000).copy(phonetic = "yo")),
            translation = "译文",
            alignment = KaraokeAlignment.Unspecified,
            start = 1_000,
            end = 2_000,
        )
        val lyrics = com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics(lines = listOf(line))

        val translationLines = lyrics.withSecondaryLineMode(LyricsSecondaryLineMode.TRANSLATION)
        val first = translationLines.lines[0] as KaraokeLine.MainKaraokeLine
        assertEquals("译文", first.translation)
        assertNull(first.phonetic)
        assertNull(first.syllables.first().phonetic)
        assertNull(first.phoneticForDisplay())
    }

    @Test
    fun `lines without phonetics fall back to their translation`() {
        val onlyRoma = LRCParser.parse(mainLrc, translation, null)
        val phoneticLines = onlyRoma.withSecondaryLineMode(LyricsSecondaryLineMode.PHONETIC)
        val first = phoneticLines.lines[0]
        assertEquals("奔向夜晚", first.translationForDisplay())
    }

    @Test
    fun `off mode clears the secondary line`() {
        val lyrics = LRCParser.parse(mainLrc, translation, roma)
        val noneLines = lyrics.withSecondaryLineMode(LyricsSecondaryLineMode.NONE)
        noneLines.lines.forEach { line ->
            assertNull(line.translationForDisplay())
        }
    }

    @Test
    fun `availability detection covers both fields`() {
        val withBoth = LRCParser.parse(mainLrc, translation, roma)
        assertEquals(true, withBoth.hasDisplayableTranslation())
        assertEquals(true, withBoth.hasDisplayablePhonetic())

        val plain = LRCParser.parse(mainLrc, null, null)
        assertEquals(false, plain.hasDisplayableTranslation())
        assertEquals(false, plain.hasDisplayablePhonetic())
    }
}

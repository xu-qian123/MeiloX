package com.ljyh.mei.utils.lyric

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizationMergeTest {
    private val mainLrc = """
        [00:01.00]夜に駆ける
        [00:05.00]沈むように溶けてゆく
        [00:09.00]僕の心
    """.trimIndent()

    private val romaLrc = """
        [00:01.00]yoru ni kakeru
        [00:05.00]shizumu you ni tokete yuku
    """.trimIndent()

    @Test
    fun `romanization lrc parsing skips comments and metadata`() {
        val parsed = parseRomanizationLrc(
            """
            [ti:test]
            [00:01.00]kimi no koe
            [00:02.00]// instrumental
            [00:03.00]boku no uta
            """.trimIndent(),
        )
        assertEquals(2, parsed.size)
        assertEquals(1000, parsed[0].timeMs)
        assertEquals("kimi no koe", parsed[0].text)
        assertEquals(3000, parsed[1].timeMs)
    }

    @Test
    fun `nearest line wins and distant lines are skipped`() {
        val lines = parseRomanizationLrc(romaLrc)
        assertEquals("yoru ni kakeru", matchRomanizationLine(lines, 1_000))
        assertEquals("yoru ni kakeru", matchRomanizationLine(lines, 1_400))
        assertEquals("shizumu you ni tokete yuku", matchRomanizationLine(lines, 5_200))
        assertNull(matchRomanizationLine(lines, 20_000))
    }

    @Test
    fun `lrc lines are upgraded to karaoke lines carrying phonetics`() {
        val lyrics = LRCParser.parse(mainLrc, null, romaLrc)
        val first = lyrics.lines[0]
        assertTrue(first is KaraokeLine.MainKaraokeLine)
        assertEquals("yoru ni kakeru", (first as KaraokeLine.MainKaraokeLine).phonetic)
        assertEquals("夜に駆ける", first.syllables.joinToString("") { it.content })

        // 第三条没有音译，保持为普通 SyncedLine。
        assertEquals("僕の心", (lyrics.lines[2] as com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine).content)
    }

    @Test
    fun `translation and romanization can coexist`() {
        val translation = """
            [00:01.00]奔向夜晚
            [00:05.00]像沉没般融化
        """.trimIndent()
        val lyrics = LRCParser.parse(mainLrc, translation, romaLrc)
        val first = lyrics.lines[0] as KaraokeLine.MainKaraokeLine
        assertEquals("yoru ni kakeru", first.phonetic)
        assertEquals("奔向夜晚", first.translation)
    }

    @Test
    fun `yrc lines receive line level phonetics`() {
        val yrc = "[1000,4000](1000,4000,0)夜に駆ける"
        val lyrics = YRCParser.parse(yrc, null, romaLrc)
        val first = lyrics.lines.first() as KaraokeLine.MainKaraokeLine
        assertEquals("yoru ni kakeru", first.phonetic)
    }
}

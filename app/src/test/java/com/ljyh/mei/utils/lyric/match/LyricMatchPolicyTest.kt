package com.ljyh.mei.utils.lyric.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricMatchPolicyTest {

    private val request = LyricMatchRequest(
        keyword = "Song Artist",
        trackName = "Song",
        artistName = "Artist",
        durationMs = 200_000L,
    )

    private fun candidate(
        source: LyricMatchSource,
        title: String = "Song",
        artist: String = "Artist",
        durationMs: Long = 200_000L,
        lyrics: String,
        format: LyricMatchFormat = LyricMatchFormat.LRC,
        translatedLyrics: String? = null,
    ) = LyricMatchCandidate(
        id = "$source-$title",
        source = source,
        title = title,
        artist = artist,
        album = null,
        durationMs = durationMs,
        lyrics = lyrics,
        translatedLyrics = translatedLyrics,
        format = format,
    )

    private val plainLrc = "[00:01.00]first\n[00:05.00]second"
    private val wordTimed = "[00:01.00]<00:01.00>first<00:01.50>second"

    @Test
    fun titleScoringMatchesTheReferenceScale() {
        assertEquals(80, scoreLyricMatchTitle("Song", "Song"))
        assertEquals(68, scoreLyricMatchTitle("Song", "Song (Live)"))
        assertEquals(0, scoreLyricMatchTitle("Song", ""))
    }

    @Test
    fun versionModifiersBreakReliableIdentity() {
        assertTrue(isReliableLyricMatchIdentity("Song", "Artist", "Song", "Artist"))
        assertFalse(isReliableLyricMatchIdentity("Song", "Artist", "Song (Remix)", "Artist"))
    }

    @Test
    fun detectionScoresWordTimedCandidatesWithTheBonus() {
        val ranked = rankLyricMatches(
            request,
            listOf(candidate(LyricMatchSource.QQ_MUSIC, lyrics = wordTimed, format = LyricMatchFormat.ENHANCED_LRC)),
        )
        assertTrue(ranked.first().hasWordTiming)
    }

    @Test
    fun preferWordTimedOutranksAHigherScoringLineResult() {
        val lineResult = candidate(
            LyricMatchSource.QQ_MUSIC,
            durationMs = 200_000L,
            lyrics = plainLrc,
            format = LyricMatchFormat.LRC,
        )
        val wordResult = candidate(
            LyricMatchSource.KUGOU,
            durationMs = 208_000L,
            lyrics = wordTimed,
            format = LyricMatchFormat.ENHANCED_LRC,
        )
        val ranked = rankLyricMatches(request, listOf(lineResult, wordResult))
        assertTrue(ranked.first().hasWordTiming)
    }

    @Test
    fun amllWinsTiesAgainstOtherSources() {
        val amll = candidate(
            LyricMatchSource.AMLL_TTML,
            lyrics = wordTimed,
            format = LyricMatchFormat.ENHANCED_LRC,
        )
        val qq = candidate(
            LyricMatchSource.QQ_MUSIC,
            lyrics = plainLrc,
            format = LyricMatchFormat.LRC,
        )
        val ranked = rankLyricMatches(request, listOf(qq, amll))
        assertEquals(LyricMatchSource.AMLL_TTML, ranked.first().candidate.source)
    }

    @Test
    fun sourcePriorityOrderIsAmllFirst() {
        val priorities = LyricMatchSource.entries.sortedByDescending { it.priority }.map { it.name }
        assertEquals(
            listOf("AMLL_TTML", "KUGOU", "CLOUD_MUSIC", "QQ_MUSIC"),
            priorities,
        )
    }

    @Test
    fun collapsedTimelinesAreRejected() {
        val collapsed = "[00:01.00]a\n[00:01.00]b\n[00:01.00]c"
        val ranked = rankLyricMatches(
            request,
            listOf(candidate(LyricMatchSource.QQ_MUSIC, lyrics = collapsed)),
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun unrelatedCandidateIsRejected() {
        val ranked = rankLyricMatches(
            request,
            listOf(candidate(LyricMatchSource.QQ_MUSIC, title = "Totally Different", artist = "Nobody", lyrics = plainLrc)),
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun durationDeltaIsReportedForRanking() {
        val ranked = rankLyricMatches(
            request,
            listOf(candidate(LyricMatchSource.CLOUD_MUSIC, durationMs = 203_000L, lyrics = plainLrc)),
        )
        assertEquals(3_000L, ranked.first().durationDeltaMs)
    }
}

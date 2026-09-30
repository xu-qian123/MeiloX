package com.ljyh.mei.utils.lyric

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalLyricSidecarTest {
    @Test
    fun `candidates follow the NeriPlayer naming`() {
        val candidates = localLyricSidecarCandidates("song.mp3")

        assertEquals(
            listOf("song.lrc", "song.lrc.txt", "song.txt"),
            candidates["lyric"],
        )
        assertEquals(
            listOf("song_trans.lrc", "song_trans.lrc.txt", "song_trans.txt"),
            candidates["translation"],
        )
        assertEquals(
            listOf(
                "song_roma.lrc", "song_roma.lrc.txt", "song_roma.txt",
                "song_romalrc.lrc", "song_romalrc.lrc.txt", "song_romalrc.txt",
                "song_romanized.lrc", "song_romanized.lrc.txt", "song_romanized.txt",
            ),
            candidates["romanization"],
        )
    }

    @Test
    fun `audio files without extension keep the whole name as base`() {
        assertEquals(
            listOf("track.lrc", "track.lrc.txt", "track.txt"),
            localLyricSidecarCandidates("track")["lyric"],
        )
    }
}

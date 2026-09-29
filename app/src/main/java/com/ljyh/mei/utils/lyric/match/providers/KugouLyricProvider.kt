package com.ljyh.mei.utils.lyric.match.providers

import com.ljyh.mei.data.network.kugou.KugouLyricsClient
import com.ljyh.mei.utils.lyric.match.LyricMatchCandidate
import com.ljyh.mei.utils.lyric.match.LyricMatchFormat
import com.ljyh.mei.utils.lyric.match.LyricMatchRequest
import com.ljyh.mei.utils.lyric.match.LyricMatchSource
import com.ljyh.mei.utils.lyric.match.isExternalLyricDurationCompatible
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kugou Music provider: song search + KRC (word-timed, converted to YRC) or
 * LRC fallback. Second in the automatic fallback order (after AMLL).
 */
@Singleton
class KugouLyricProvider @Inject constructor() : LyricMatchSourceProvider {

    private val client = KugouLyricsClient()

    override val source: LyricMatchSource = LyricMatchSource.KUGOU

    override suspend fun search(request: LyricMatchRequest): List<LyricMatchCandidate> {
        val keyword = request.keyword.ifBlank { request.trackName }.trim()
        if (keyword.isBlank()) return emptyList()

        val songs = client.searchSongs(keyword)
            .filter { song ->
                request.durationMs <= 0L || song.durationMs <= 0L ||
                    isExternalLyricDurationCompatible(request.durationMs, song.durationMs)
            }
            .take(MAX_DETAIL_LOOKUPS)

        return songs.mapNotNull { song ->
            val payload = client.getBestLyricPayload(song) ?: return@mapNotNull null
            val lyrics = payload.lyrics.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LyricMatchCandidate(
                id = song.hash,
                source = source,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                lyrics = lyrics,
                translatedLyrics = payload.translatedLyrics,
                format = if (lyrics.startsWith("[")) LyricMatchFormat.YRC else LyricMatchFormat.LRC,
            )
        }
    }

    private companion object {
        const val MAX_DETAIL_LOOKUPS = 4
    }
}

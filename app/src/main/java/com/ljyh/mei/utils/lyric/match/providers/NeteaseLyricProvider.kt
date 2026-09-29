package com.ljyh.mei.utils.lyric.match.providers

import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.PlayerRepository
import com.ljyh.mei.data.repository.SearchRepository
import com.ljyh.mei.utils.lyric.match.LyricMatchCandidate
import com.ljyh.mei.utils.lyric.match.LyricMatchFormat
import com.ljyh.mei.utils.lyric.match.LyricMatchRequest
import com.ljyh.mei.utils.lyric.match.LyricMatchSource
import com.ljyh.mei.utils.lyric.match.isExternalLyricDurationCompatible
import javax.inject.Inject
import javax.inject.Singleton

/** NetEase Cloud Music provider: song search → lyric v1 (YRC preferred). */
@Singleton
class NeteaseLyricProvider @Inject constructor(
    private val searchRepository: SearchRepository,
    private val playerRepository: PlayerRepository,
) : LyricMatchSourceProvider {

    override val source: LyricMatchSource = LyricMatchSource.CLOUD_MUSIC

    override suspend fun search(request: LyricMatchRequest): List<LyricMatchCandidate> {
        val keyword = request.keyword.ifBlank { request.trackName }.trim()
        if (keyword.isBlank()) return emptyList()

        val searchResult = searchRepository.search(keyword, type = 1, limit = 10)
        val songs = (searchResult as? Resource.Success)?.data?.result?.songs.orEmpty()
        val wantedDuration = request.durationMs
        val candidates = songs
            .filter { song ->
                wantedDuration <= 0L || song.duration <= 0L ||
                    isExternalLyricDurationCompatible(wantedDuration, song.duration)
            }
            .take(MAX_DETAIL_LOOKUPS)

        return candidates.mapNotNull { song ->
            val lyric = (playerRepository.getLyricV1(song.id.toString()) as? Resource.Success)
                ?.data ?: return@mapNotNull null
            val yrc = lyric.yrc?.lyric?.takeIf { it.isNotBlank() }
            val lrc = lyric.lrc?.lyric?.takeIf { it.isNotBlank() }
            val lyrics = yrc ?: lrc ?: return@mapNotNull null
            val translation = lyric.ytlrc?.lyric?.takeIf { it.isNotBlank() }
                ?: lyric.tlyric?.lyric?.takeIf { it.isNotBlank() }
            LyricMatchCandidate(
                id = song.id.toString(),
                source = source,
                title = song.name,
                artist = song.artists.joinToString(" / ") { it.name },
                album = song.album.name,
                durationMs = song.duration,
                lyrics = lyrics,
                translatedLyrics = translation,
                format = if (yrc != null) LyricMatchFormat.YRC else LyricMatchFormat.LRC,
            )
        }
    }

    private companion object {
        const val MAX_DETAIL_LOOKUPS = 4
    }
}

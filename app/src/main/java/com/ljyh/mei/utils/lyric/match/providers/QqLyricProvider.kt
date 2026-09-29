package com.ljyh.mei.utils.lyric.match.providers

import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.PlayerRepository
import com.ljyh.mei.utils.encrypt.QRCUtils
import com.ljyh.mei.utils.lyric.match.LyricMatchCandidate
import com.ljyh.mei.utils.lyric.match.LyricMatchFormat
import com.ljyh.mei.utils.lyric.match.LyricMatchRequest
import com.ljyh.mei.utils.lyric.match.LyricMatchSource
import javax.inject.Inject
import javax.inject.Singleton

/** QQ Music provider: search → QRC (decoded) or LRC fallback. */
@Singleton
class QqLyricProvider @Inject constructor(
    private val playerRepository: PlayerRepository,
) : LyricMatchSourceProvider {

    override val source: LyricMatchSource = LyricMatchSource.QQ_MUSIC

    override suspend fun search(request: LyricMatchRequest): List<LyricMatchCandidate> {
        val keyword = request.keyword.ifBlank { request.trackName }.trim()
        if (keyword.isBlank()) return emptyList()

        val searchResult = playerRepository.searchNew(keyword)
        val songs = (searchResult as? Resource.Success)
            ?.data?.request?.data?.body?.itemSong.orEmpty()
        val wantedSeconds = request.durationMs / 1_000L
        val candidates = songs
            .filter { song ->
                wantedSeconds <= 0L || kotlin.math.abs(song.interval - wantedSeconds) <= MAX_DURATION_DRIFT_SECONDS
            }
            .take(MAX_DETAIL_LOOKUPS)

        return candidates.mapNotNull { song ->
            val artist = song.singer.joinToString(", ") { it.name }
            val lyricResult = playerRepository.getLyricNew(
                title = song.title,
                album = song.album.title,
                artist = artist,
                duration = song.interval,
                id = song.id,
            ) as? Resource.Success ?: return@mapNotNull null
            val data = lyricResult.data.musicMusichallSongPlayLyricInfoGetPlayLyricInfo?.data
                ?: return@mapNotNull null
            val isQrc = data.qrcT != 0
            val decoded = runCatching {
                data.copy(
                    lyric = QRCUtils.decodeLyric(data.lyric),
                    trans = QRCUtils.decodeLyric(data.trans, true),
                    roma = QRCUtils.decodeLyric(data.roma),
                )
            }.getOrElse { data }
            val lyrics = decoded.lyric.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LyricMatchCandidate(
                id = song.id.toString(),
                source = source,
                title = song.title,
                artist = artist,
                album = song.album.title,
                durationMs = song.interval * 1_000L,
                lyrics = lyrics,
                translatedLyrics = decoded.trans.takeIf { it.isNotBlank() },
                format = if (isQrc) LyricMatchFormat.YRC else LyricMatchFormat.LRC,
            )
        }
    }

    private companion object {
        const val MAX_DETAIL_LOOKUPS = 4
        const val MAX_DURATION_DRIFT_SECONDS = 8L
    }
}

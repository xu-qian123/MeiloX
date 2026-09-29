package com.ljyh.mei.utils.lyric.match.providers

import com.ljyh.mei.data.model.AmllLyricSearchResult
import com.ljyh.mei.data.repository.PlayerRepository
import com.ljyh.mei.utils.lyric.edit.normalizeLyricText
import com.ljyh.mei.utils.lyric.match.LyricMatchCandidate
import com.ljyh.mei.utils.lyric.match.LyricMatchFormat
import com.ljyh.mei.utils.lyric.match.LyricMatchRequest
import com.ljyh.mei.utils.lyric.match.LyricMatchSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AMLL TTML DB provider: keyword search + raw TTML fetch.
 * Highest priority source (duet roles + word timing).
 */
@Singleton
class AmllLyricProvider @Inject constructor(
    private val repository: PlayerRepository,
) : LyricMatchSourceProvider {

    override val source: LyricMatchSource = LyricMatchSource.AMLL_TTML

    override suspend fun search(request: LyricMatchRequest): List<LyricMatchCandidate> {
        val trackName = request.trackName.trim()
        if (trackName.isBlank()) return emptyList()
        val results = repository.searchAMLLLyrics(trackName)
            .filter { result ->
                scoreAmllSearchResult(trackName, request.artistName, result) >= MIN_AMLL_MATCH_SCORE
            }
            .sortedByDescending { result -> scoreAmllSearchResult(trackName, request.artistName, result) }
            .take(MAX_CANDIDATES)
        return results.mapNotNull { result ->
            val ttml = repository.getAMLLLyricRaw(result.file) ?: return@mapNotNull null
            LyricMatchCandidate(
                id = result.file,
                source = source,
                title = result.title,
                artist = result.artists.firstOrNull() ?: result.artist,
                album = result.albums.firstOrNull(),
                durationMs = 0L,
                lyrics = ttml,
                translatedLyrics = null,
                format = LyricMatchFormat.TTML,
            )
        }
    }

    private companion object {
        const val MAX_CANDIDATES = 5
        const val MIN_AMLL_MATCH_SCORE = 70
        const val MIN_AMLL_ARTIST_MATCH_SCORE = 30
    }

    private fun scoreAmllSearchResult(
        trackName: String,
        artistName: String,
        result: AmllLyricSearchResult,
    ): Int {
        val requestedTitle = normalizeLyricText(trackName)
        val requestedArtists = splitAmllArtists(artistName)
        if (requestedTitle.isBlank()) return 0

        val titleScore = result.titles
            .ifEmpty { listOf(result.title) }
            .maxOfOrNull { candidate ->
                val normalized = normalizeLyricText(candidate)
                when {
                    normalized.isBlank() -> 0
                    normalized == requestedTitle -> 90
                    normalized.startsWith("$requestedTitle ") -> 78
                    normalized.contains(requestedTitle) || requestedTitle.contains(normalized) -> 64
                    else -> tokenOverlapScore(requestedTitle, normalized) * 6
                }
            } ?: 0

        val artistScore = if (requestedArtists.isEmpty()) {
            0
        } else {
            result.artists
                .ifEmpty { listOf(result.artist) }
                .maxOfOrNull { candidate ->
                    val normalized = normalizeLyricText(candidate)
                    requestedArtists.maxOf { requested ->
                        when {
                            normalized.isBlank() -> 0
                            normalized == requested -> 55
                            normalized.contains(requested) || requested.contains(normalized) -> 40
                            else -> tokenOverlapScore(requested, normalized) * 8
                        }
                    }
                } ?: 0
        }

        if (requestedArtists.isNotEmpty() && artistScore < MIN_AMLL_ARTIST_MATCH_SCORE) {
            return 0
        }
        return titleScore + artistScore
    }

    private fun splitAmllArtists(value: String): List<String> {
        return value.split(Regex("""[/,，、&+]|(?:\s+x\s+)""", RegexOption.IGNORE_CASE))
            .map(::normalizeLyricText)
            .filter { it.isNotBlank() }
    }

    private fun tokenOverlapScore(left: String, right: String): Int {
        val leftTokens = left.split(' ').filter { it.isNotBlank() }.toSet()
        val rightTokens = right.split(' ').filter { it.isNotBlank() }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0
        return leftTokens.intersect(rightTokens).size
    }
}

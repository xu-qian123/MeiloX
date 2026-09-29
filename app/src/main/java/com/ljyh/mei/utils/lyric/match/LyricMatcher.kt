package com.ljyh.mei.utils.lyric.match

import com.ljyh.mei.utils.lyric.match.providers.AmllLyricProvider
import com.ljyh.mei.utils.lyric.match.providers.KugouLyricProvider
import com.ljyh.mei.utils.lyric.match.providers.LyricMatchSourceProvider
import com.ljyh.mei.utils.lyric.match.providers.NeteaseLyricProvider
import com.ljyh.mei.utils.lyric.match.providers.QqLyricProvider
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-source lyric matcher.
 *
 * Manual search merges every selected source and ranks the result;
 * the automatic word-timed fallback walks [automaticWordTimedLyricSourceOrder]
 * (AMLL first) and adopts the first HIGH-confidence word-timed candidate.
 */
@Singleton
class LyricMatcher @Inject constructor(
    amllLyricProvider: AmllLyricProvider,
    kugouLyricProvider: KugouLyricProvider,
    neteaseLyricProvider: NeteaseLyricProvider,
    qqLyricProvider: QqLyricProvider,
) {
    private val providers: Map<LyricMatchSource, LyricMatchSourceProvider> =
        listOf(amllLyricProvider, kugouLyricProvider, neteaseLyricProvider, qqLyricProvider)
            .associateBy { it.source }

    suspend fun matchLyrics(request: LyricMatchRequest): List<RankedLyricMatch> {
        val candidates = mutableListOf<LyricMatchCandidate>()
        for (source in manualLyricMatchSourceOrder) {
            if (source !in request.sources) continue
            candidates += searchSource(request, source)
        }
        return rankLyricMatches(request, candidates).take(MAX_RESULTS)
    }

    suspend fun matchHighConfidenceForSource(
        request: LyricMatchRequest,
        source: LyricMatchSource,
    ): List<RankedLyricMatch> {
        return rankLyricMatches(request, searchSource(request, source))
    }

    private suspend fun searchSource(
        request: LyricMatchRequest,
        source: LyricMatchSource,
    ): List<LyricMatchCandidate> {
        val provider = providers[source] ?: return emptyList()
        return runCatching { provider.search(request) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                Timber.tag(TAG).w(error, "Lyric match source $source failed")
            }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "LyricMatcher"
        const val MAX_RESULTS = 20
    }
}

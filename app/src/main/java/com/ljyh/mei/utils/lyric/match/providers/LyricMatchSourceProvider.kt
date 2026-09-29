package com.ljyh.mei.utils.lyric.match.providers

import com.ljyh.mei.utils.lyric.match.LyricMatchCandidate
import com.ljyh.mei.utils.lyric.match.LyricMatchRequest
import com.ljyh.mei.utils.lyric.match.LyricMatchSource

/** One searchable lyric source for the multi-source matcher. */
interface LyricMatchSourceProvider {
    val source: LyricMatchSource
    suspend fun search(request: LyricMatchRequest): List<LyricMatchCandidate>
}

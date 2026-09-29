package com.ljyh.mei.utils.lyric.match

/**
 * Multi-source lyric matching models.
 *
 * Ported from NeriPlayer's `EditableLyricMatch*` set, with the source priority
 * re-ordered so AMLL/TTML wins: TTML carries `ttm:agent` duet roles and the
 * best word-level scrolling.
 */

enum class LyricMatchSource(val priority: Int) {
    AMLL_TTML(5),
    KUGOU(4),
    CLOUD_MUSIC(3),
    QQ_MUSIC(2),
}

enum class LyricMatchFormat(val formatScore: Int) {
    TTML(16),
    YRC(14),
    ENHANCED_LRC(14),
    LRC(10),
    PLAIN(2),
}

enum class LyricMatchConfidence(val rank: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2),
}

data class LyricMatchRequest(
    val keyword: String,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val durationMs: Long = 0L,
    val preferWordTimed: Boolean = true,
    val sources: Set<LyricMatchSource> = defaultLyricMatchSources(),
)

data class LyricMatchCandidate(
    val id: String,
    val source: LyricMatchSource,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val lyrics: String,
    val translatedLyrics: String? = null,
    val format: LyricMatchFormat = LyricMatchFormat.LRC,
    val sourceScore: Int = 0,
)

data class RankedLyricMatch(
    val candidate: LyricMatchCandidate,
    val score: Int,
    val durationDeltaMs: Long?,
    val confidence: LyricMatchConfidence = LyricMatchConfidence.LOW,
    /** 歌词自身是否带逐词时间轴；独立于 [LyricMatchRequest.preferWordTimed]，供 UI 标记。 */
    val hasWordTiming: Boolean = false,
)

fun defaultLyricMatchSources(): Set<LyricMatchSource> = LyricMatchSource.entries.toSet()

/** 播放期自动兜底顺序（AMLL/TTML 优先）。 */
val automaticWordTimedLyricSourceOrder = listOf(
    LyricMatchSource.AMLL_TTML,
    LyricMatchSource.KUGOU,
    LyricMatchSource.QQ_MUSIC,
    LyricMatchSource.CLOUD_MUSIC,
)

/** 手动搜索的聚合顺序（结果仍按打分排序，这里只决定请求先后）。 */
val manualLyricMatchSourceOrder = listOf(
    LyricMatchSource.AMLL_TTML,
    LyricMatchSource.KUGOU,
    LyricMatchSource.CLOUD_MUSIC,
    LyricMatchSource.QQ_MUSIC,
)

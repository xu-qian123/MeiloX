package com.ljyh.mei.utils.lyric.match

import com.ljyh.mei.utils.lyric.edit.LyricFormatDetector
import com.ljyh.mei.utils.lyric.edit.normalizeLyricText
import com.ljyh.mei.utils.lyric.edit.toSimplifiedChinese
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scoring / ranking for multi-source lyric candidates.
 *
 * Ported from NeriPlayer's `EditableLyricMatchPolicy`, with MeiloX tweaks:
 * - AMLL/TTML carries the highest source priority (duet roles + word timing);
 * - word-timing detection delegates to [LyricFormatDetector] (string-only);
 * - text normalization reuses the shared [normalizeLyricText].
 */

private const val MIN_LYRIC_MATCH_SCORE = 35
private const val WORD_TIMED_LYRIC_MATCH_BONUS = 36
private const val MIN_RELIABLE_LYRIC_TITLE_SCORE = 52
private const val MIN_RELIABLE_LYRIC_ARTIST_SCORE = 24
private const val AMLL_SOURCE_QUALITY_BONUS = 5
private const val DEFAULT_SOURCE_QUALITY_BONUS = 4

private val lyricMatchHardArtistSeparatorRegex = Regex("[/,，、&+]|\\s+[xX]\\s+")
private val lyricMatchFeaturedArtistSeparatorRegex = Regex(
    """\b(?:feat\.?|ft\.?|featuring)\b""",
    RegexOption.IGNORE_CASE,
)
private val lyricMatchArtistCollaborationConnectives = setOf(
    "and", "with", "x", "vs", "versus", "和", "与",
)

fun lyricMatchSourcePriority(source: LyricMatchSource): Int = source.priority

fun rankLyricMatches(
    request: LyricMatchRequest,
    candidates: List<LyricMatchCandidate>,
): List<RankedLyricMatch> {
    return candidates.asSequence()
        .filter { it.lyrics.isNotBlank() }
        .filterNot { LyricTimelinePolicy.hasCollapsedTimedLyricTimeline(it.lyrics) }
        .mapNotNull { candidate ->
            val titleScore = scoreLyricMatchTitle(request.trackName, candidate.title)
            val artistScore = scoreLyricMatchArtist(request.artistName, candidate.artist)
            val albumScore = scoreLyricMatchAlbum(request.albumName.orEmpty(), candidate.album.orEmpty())
            val durationScore = scoreLyricMatchDuration(request.durationMs, candidate.durationMs)
            val qualityScore = scoreLyricMatchQuality(candidate)
            val hasWordTiming = LyricFormatDetector.hasWordTiming(candidate.lyrics)
            val wordTimingScore =
                if (request.preferWordTimed && hasWordTiming) WORD_TIMED_LYRIC_MATCH_BONUS else 0
            val keywordScore = scoreLyricMatchKeyword(request.keyword, candidate)
            val canUseKeywordFallback = hasPlaceholderLyricMetadata(request.trackName) ||
                hasPlaceholderLyricMetadata(request.artistName)
            val hasPrimaryArtist = hasPrimaryLyricMatchArtist(request.artistName, candidate.artist)
            val hasDurationSignal = request.durationMs <= 0L || candidate.durationMs <= 0L ||
                isExternalLyricDurationCompatible(request.durationMs, candidate.durationMs)
            val hasReliableIdentity = isReliableLyricMatchIdentity(
                expectedTitle = request.trackName,
                expectedArtist = request.artistName,
                candidateTitle = candidate.title,
                candidateArtist = candidate.artist,
            )
            val hasPlausibleIdentity = isPlausibleLyricMatchIdentity(
                expectedTitle = request.trackName,
                expectedArtist = request.artistName,
                candidateTitle = candidate.title,
                candidateArtist = candidate.artist,
                durationCompatible = hasDurationSignal,
            ) || (canUseKeywordFallback && (keywordScore > 0 || titleScore >= 20))
            if (!hasPlausibleIdentity) {
                return@mapNotNull null
            }
            val score = titleScore +
                artistScore +
                albumScore +
                durationScore +
                qualityScore +
                wordTimingScore +
                keywordScore +
                candidate.sourceScore.coerceIn(0, 20)
            if (score < MIN_LYRIC_MATCH_SCORE) {
                return@mapNotNull null
            }
            val confidence = when {
                hasReliableIdentity && hasDurationSignal -> LyricMatchConfidence.HIGH
                hasPrimaryArtist && hasDurationSignal && titleScore >= 20 ->
                    LyricMatchConfidence.MEDIUM

                titleScore >= MIN_RELIABLE_LYRIC_TITLE_SCORE && hasDurationSignal ->
                    LyricMatchConfidence.MEDIUM

                else -> LyricMatchConfidence.LOW
            }
            RankedLyricMatch(
                candidate = candidate,
                score = score,
                durationDeltaMs = durationDeltaMs(request.durationMs, candidate.durationMs),
                confidence = confidence,
                hasWordTiming = hasWordTiming,
            )
        }
        .sortedWith(lyricMatchResultComparator(preferWordTimed = request.preferWordTimed))
        .toList()
}

internal fun lyricMatchResultComparator(
    preferWordTimed: Boolean = false,
): Comparator<RankedLyricMatch> {
    // 偏好逐词时把逐词结果整体提为一档，而不是只加逐词分：否则「高置信度的逐行结果」
    // 仍会压过「中置信度的逐词结果」，与「优先使用逐词歌词」的语义不符。
    val wordTimingTier: Comparator<RankedLyricMatch> =
        if (preferWordTimed) {
            compareByDescending { it.hasWordTiming }
        } else {
            compareBy { 0 }
        }
    return wordTimingTier
        .thenByDescending { it.confidence.rank }
        .thenByDescending { it.score }
        .thenBy { it.durationDeltaMs ?: Long.MAX_VALUE }
        .thenByDescending { it.candidate.source.priority }
        .thenBy { normalizeLyricText(it.candidate.title) }
}

fun hasLyricMatchSignal(
    request: LyricMatchRequest,
    candidate: LyricMatchCandidate,
): Boolean {
    val titleScore = scoreLyricMatchTitle(request.trackName, candidate.title)
    val artistScore = scoreLyricMatchArtist(request.artistName, candidate.artist)
    val keywordScore = scoreLyricMatchKeyword(request.keyword, candidate)
    return (titleScore >= 20 || keywordScore > 0) &&
        (artistScore >= MIN_RELIABLE_LYRIC_ARTIST_SCORE || candidate.artist.isBlank())
}

fun scoreLyricMatchTitle(expected: String, candidate: String): Int {
    val expectedText = normalizeLyricText(expected)
    val candidateText = normalizeLyricText(candidate)
    if (expectedText.isBlank() || candidateText.isBlank()) return 0
    return when {
        candidateText == expectedText -> 80
        candidateText.startsWith("$expectedText ") -> 68
        expectedText.startsWith("$candidateText ") -> 62
        candidateText.contains(expectedText) || expectedText.contains(candidateText) -> 52
        else -> (tokenOverlapRatio(expectedText, candidateText) * 44).roundToInt()
    }
}

fun scoreLyricMatchArtist(expected: String, candidate: String): Int {
    val expectedArtists = splitLyricMatchArtists(expected)
    val candidateArtists = splitLyricMatchArtists(candidate)
    if (expectedArtists.isEmpty() || candidateArtists.isEmpty()) return 0
    if (expectedArtists == candidateArtists) return 55
    if (candidateArtists.containsAll(expectedArtists)) return 46
    val intersectionSize = expectedArtists.intersect(candidateArtists).size
    if (intersectionSize > 0) {
        return 28 + (18 * intersectionSize / expectedArtists.size.coerceAtLeast(1))
    }
    return expectedArtists.maxOf { expectedArtist ->
        candidateArtists.maxOf { candidateArtist ->
            when {
                hasAlignedLyricMatchArtistContainment(expectedArtist, candidateArtist) -> 24
                else -> (tokenOverlapRatio(expectedArtist, candidateArtist) * 20).roundToInt()
            }
        }
    }
}

fun isReliableLyricMatchIdentity(
    expectedTitle: String,
    expectedArtist: String,
    candidateTitle: String,
    candidateArtist: String,
): Boolean {
    if (
        expectedTitle.isBlank() ||
        expectedArtist.isBlank() ||
        candidateTitle.isBlank() ||
        candidateArtist.isBlank()
    ) {
        return false
    }
    if (!hasCompatibleLyricVersion(expectedTitle, candidateTitle)) {
        return false
    }
    val expectedPrimaryArtist = primaryLyricMatchArtist(expectedArtist)
    val candidateArtists = splitLyricMatchArtists(candidateArtist)
    val primaryArtistMatches = expectedPrimaryArtist != null && candidateArtists.any { candidateArtistName ->
        candidateArtistName == expectedPrimaryArtist ||
            hasCollaboratorLyricMatchArtistSuffix(candidateArtistName, expectedPrimaryArtist)
    }
    return primaryArtistMatches &&
        canonicalLyricMatchTitle(expectedTitle) == canonicalLyricMatchTitle(candidateTitle) &&
        scoreLyricMatchTitle(expectedTitle, candidateTitle) >= MIN_RELIABLE_LYRIC_TITLE_SCORE &&
        scoreLyricMatchArtist(expectedArtist, candidateArtist) >= MIN_RELIABLE_LYRIC_ARTIST_SCORE
}

fun isPlausibleLyricMatchIdentity(
    expectedTitle: String,
    expectedArtist: String,
    candidateTitle: String,
    candidateArtist: String,
    durationCompatible: Boolean,
): Boolean {
    if (isReliableLyricMatchIdentity(expectedTitle, expectedArtist, candidateTitle, candidateArtist)) {
        return true
    }
    val titleScore = scoreLyricMatchTitle(expectedTitle, candidateTitle)
    val artistScore = scoreLyricMatchArtist(expectedArtist, candidateArtist)
    val hasPrimaryArtist = hasPrimaryLyricMatchArtist(expectedArtist, candidateArtist)
    return (hasPrimaryArtist && titleScore >= 20 && durationCompatible) ||
        (titleScore >= MIN_RELIABLE_LYRIC_TITLE_SCORE && durationCompatible && candidateArtist.isBlank()) ||
        (titleScore >= 20 && artistScore >= MIN_RELIABLE_LYRIC_ARTIST_SCORE && durationCompatible)
}

fun scoreLyricMatchDuration(expectedDurationMs: Long, candidateDurationMs: Long): Int {
    if (expectedDurationMs <= 0L || candidateDurationMs <= 0L) return 0
    val deltaMs = abs(expectedDurationMs - candidateDurationMs)
    if (isExternalLyricDurationCompatible(expectedDurationMs, candidateDurationMs)) {
        return (42 - deltaMs / 500L).toInt().coerceAtLeast(22)
    }
    return -(deltaMs / 3_000L).toInt().coerceAtMost(48)
}

fun scoreLyricMatchKeyword(keyword: String, candidate: LyricMatchCandidate): Int {
    val query = toSimplifiedChinese(keyword.trim())
    if (query.isBlank()) return 0
    val fuzzyScore = SearchTextMatcher.score(
        query = query,
        values = listOf(candidate.title, candidate.artist, candidate.album.orEmpty())
            .map { value -> toSimplifiedChinese(value) },
    ) ?: return 0
    return (48 - fuzzyScore / 2).coerceIn(12, 48)
}

fun scoreLyricMatchAlbum(expected: String, candidate: String): Int {
    val expectedAlbum = normalizeLyricText(expected)
    val candidateAlbum = normalizeLyricText(candidate)
    if (expectedAlbum.isBlank() || candidateAlbum.isBlank()) return 0
    return when {
        candidateAlbum == expectedAlbum -> 8
        candidateAlbum.contains(expectedAlbum) || expectedAlbum.contains(candidateAlbum) -> 4
        else -> 0
    }
}

private fun scoreLyricMatchQuality(candidate: LyricMatchCandidate): Int {
    val formatScore = candidate.format.formatScore
    val translationScore = if (!candidate.translatedLyrics.isNullOrBlank()) 5 else 0
    val sourceBonus = when (candidate.source) {
        LyricMatchSource.AMLL_TTML -> AMLL_SOURCE_QUALITY_BONUS
        else -> DEFAULT_SOURCE_QUALITY_BONUS
    }
    return formatScore + translationScore + sourceBonus
}

private fun hasPlaceholderLyricMetadata(value: String): Boolean {
    return normalizeLyricText(value) in setOf(
        "unknown",
        "unknown artist",
        "unknown song",
        "unknown title",
        "未知",
        "未知歌手",
        "未知歌曲",
        "未知标题",
    )
}

private fun hasCompatibleLyricVersion(expectedTitle: String, candidateTitle: String): Boolean {
    return lyricVersionSignature(expectedTitle) == lyricVersionSignature(candidateTitle)
}

private fun lyricVersionSignature(value: String): Set<String> {
    return lyricVersionModifierRegex.findAll(normalizeLyricText(value))
        .map { match ->
            when (match.value) {
                "remastered" -> "remaster"
                else -> match.value
            }
        }
        .toSet()
}

private fun canonicalLyricMatchTitle(value: String): String {
    return normalizeLyricText(value)
        .replace(
            Regex(
                "(?:\\s+|^)(?:official|audio|video|lyrics?|visualizer|hd|hq|4k|mv|官方|官方版|官方视频|音频|歌词|歌词版|高清|完整版)(?:\\s+(?:official|audio|video|lyrics?|visualizer|hd|hq|4k|mv|官方|官方版|官方视频|音频|歌词|歌词版|高清|完整版))*$",
            ),
            " ",
        )
        .replace(lyricVersionModifierRegex, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private val lyricVersionModifierRegex = Regex(
    """\b(?:remaster(?:ed)?|remix|live|acoustic|instrumental|karaoke|demo|cover|rework|slowed|sped\s+up|version|edit|extended|radio|clean|explicit)\b""",
)

private fun splitLyricMatchArtists(value: String): Set<String> {
    val wholeName = normalizeLyricText(value)
    val segments = lyricMatchArtistSegments(value)
    return (listOf(wholeName) + segments)
        .filter { it.isNotBlank() }
        .toSet()
}

private fun hasPrimaryLyricMatchArtist(expected: String, candidate: String): Boolean {
    val expectedPrimary = primaryLyricMatchArtist(expected) ?: return false
    return splitLyricMatchArtists(candidate).any { it == expectedPrimary }
}

private fun hasCollaboratorLyricMatchArtistSuffix(
    candidateArtistName: String,
    expectedPrimaryArtist: String,
): Boolean {
    if (!candidateArtistName.startsWith("$expectedPrimaryArtist ")) return false
    val connective = candidateArtistName
        .removePrefix("$expectedPrimaryArtist ")
        .trimStart()
        .substringBefore(' ')
    return connective in lyricMatchArtistCollaborationConnectives
}

private fun primaryLyricMatchArtist(value: String): String? {
    return lyricMatchArtistSegments(value).firstOrNull()
}

private fun lyricMatchArtistSegments(value: String): List<String> {
    return lyricMatchFeaturedArtistSeparatorRegex
        .split(value)
        .flatMap { segment -> lyricMatchHardArtistSeparatorRegex.split(segment) }
        .map(::normalizeLyricText)
        .filter { it.isNotBlank() }
}

private fun hasAlignedLyricMatchArtistContainment(left: String, right: String): Boolean {
    return when {
        left == right -> true
        right.startsWith("$left ") -> true
        else -> false
    }
}

private fun tokenOverlapRatio(left: String, right: String): Double {
    val leftTokens = left.split(' ').filter { it.isNotBlank() }.toSet()
    val rightTokens = right.split(' ').filter { it.isNotBlank() }.toSet()
    if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
    val intersectionSize = leftTokens.intersect(rightTokens).size
    return intersectionSize.toDouble() / maxOf(leftTokens.size, rightTokens.size)
}

private fun durationDeltaMs(expectedDurationMs: Long, candidateDurationMs: Long): Long? {
    if (expectedDurationMs <= 0L || candidateDurationMs <= 0L) return null
    return abs(expectedDurationMs - candidateDurationMs)
}

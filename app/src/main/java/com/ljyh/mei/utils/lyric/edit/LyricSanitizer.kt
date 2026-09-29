package com.ljyh.mei.utils.lyric.edit

import kotlin.math.abs

/**
 * Removes non-lyric lines (LRC metadata, credit lists, song identity lines)
 * from matched/candidate lyrics before they are shown for editing.
 *
 * Ported from NeriPlayer's `EditableLyricSanitizer`, string-only by design.
 */
data class SanitizedLyrics(
    val lyrics: String,
    val translatedLyrics: String?,
)

fun sanitizeMatchedLyrics(
    lyrics: String,
    translatedLyrics: String?,
    title: String,
    artist: String,
    album: String? = null,
): SanitizedLyrics {
    val lyricText = normalizeLyricLineBreaks(lyrics)
    if (lyricText.isBlank() || looksLikeTtmlLyrics(lyricText)) {
        return SanitizedLyrics(lyrics = lyrics, translatedLyrics = translatedLyrics)
    }

    val context = LyricSanitizeContext(
        title = normalizeLyricText(title),
        artistTerms = splitLyricSanitizeArtists(artist),
        album = normalizeLyricText(album.orEmpty()),
    )
    val mainLines = lyricText.lines()
    val mainInfos = mainLines.mapIndexed(::parseLyricSanitizeLine)
    val mainRemovalIndexes = mainInfos
        .filter { shouldRemoveNonLyricLine(it, context, mainInfos.size) }
        .map { it.index }
        .toSet()
    val cleanedLyrics = removeLyricLines(mainLines, mainRemovalIndexes)
    val finalLyrics = if (mainRemovalIndexes.isNotEmpty() && cleanedLyrics.isNotBlank()) {
        cleanedLyrics
    } else {
        lyrics
    }

    return SanitizedLyrics(
        lyrics = finalLyrics,
        translatedLyrics = sanitizeMatchedTranslatedLyrics(
            translatedLyrics = translatedLyrics,
            context = context,
            mainLines = mainInfos,
            mainRemovalIndexes = mainRemovalIndexes,
        ),
    )
}

private data class LyricSanitizeContext(
    val title: String,
    val artistTerms: List<String>,
    val album: String,
)

private data class LyricSanitizeLine(
    val index: Int,
    val rawLine: String,
    val text: String,
    val startMs: Long?,
    val durationMs: Long?,
)

private fun sanitizeMatchedTranslatedLyrics(
    translatedLyrics: String?,
    context: LyricSanitizeContext,
    mainLines: List<LyricSanitizeLine>,
    mainRemovalIndexes: Set<Int>,
): String? {
    val rawTranslation = translatedLyrics?.takeIf { it.isNotBlank() } ?: return translatedLyrics
    val translationText = normalizeLyricLineBreaks(rawTranslation)
    if (translationText.isBlank()) {
        return null
    }
    val translationLines = translationText.lines()
    val translationInfos = translationLines.mapIndexed(::parseLyricSanitizeLine)
    val translationRemovalIndexes = translationInfos
        .filter { info ->
            shouldRemoveNonLyricLine(info, context, translationInfos.size) ||
                shouldRemovePairedTranslatedLine(info, mainLines, mainRemovalIndexes, translationInfos.size)
        }
        .map { it.index }
        .toSet()
    if (translationRemovalIndexes.isEmpty()) {
        return translatedLyrics
    }
    return removeLyricLines(translationLines, translationRemovalIndexes)
        .takeIf { it.isNotBlank() }
}

private fun shouldRemovePairedTranslatedLine(
    translatedLine: LyricSanitizeLine,
    mainLines: List<LyricSanitizeLine>,
    mainRemovalIndexes: Set<Int>,
    translatedLineCount: Int,
): Boolean {
    if (mainLines.size != translatedLineCount || translatedLine.index !in mainRemovalIndexes) {
        return false
    }
    val mainLine = mainLines.getOrNull(translatedLine.index) ?: return false
    val mainStartMs = mainLine.startMs ?: return false
    val translatedStartMs = translatedLine.startMs ?: return false
    return abs(mainStartMs - translatedStartMs) <= TRANSLATION_PAIR_TIMESTAMP_DRIFT_MS
}

private fun shouldRemoveNonLyricLine(
    line: LyricSanitizeLine,
    context: LyricSanitizeContext,
    lineCount: Int,
): Boolean {
    if (line.text.isBlank()) {
        return false
    }
    if (lyricLrcMetadataRegex.matches(line.rawLine.trim())) {
        return true
    }
    val isEdgeMetadataLine = isEdgeMetadataLine(line.index, lineCount)
    if (isCreditLine(line.text, isEdgeMetadataLine)) {
        return true
    }
    if (!isEdgeMetadataLine) {
        return false
    }
    return isSongIdentityLine(line, context)
}

private fun isCreditLine(text: String, isEdgeMetadataLine: Boolean): Boolean {
    val trimmed = text.trim()
    if (lyricEnglishCreditRegex.matches(trimmed)) {
        return true
    }
    if (lyricChineseCreditRegex.matches(trimmed)) {
        return true
    }
    if (!isEdgeMetadataLine) {
        return false
    }
    val normalizedText = normalizeLyricText(trimmed)
    return lyricEdgeCreditPrefixes.any { prefix ->
        normalizedText == prefix || normalizedText.startsWith("$prefix ")
    }
}

private fun isSongIdentityLine(
    line: LyricSanitizeLine,
    context: LyricSanitizeContext,
): Boolean {
    val normalizedText = normalizeLyricText(line.text)
    if (normalizedText.isBlank()) {
        return false
    }
    val hasTitle = containsSanitizePhrase(normalizedText, context.title)
    val hasArtist = context.artistTerms.any { containsSanitizePhrase(normalizedText, it) }
    val hasAlbum = containsSanitizePhrase(normalizedText, context.album)
    if (hasTitle && (hasArtist || hasAlbum)) {
        return true
    }
    val shortTimedTitle = line.durationMs != null &&
        line.durationMs <= SHORT_IDENTITY_LINE_DURATION_MS &&
        line.startMs != null &&
        line.startMs <= EDGE_IDENTITY_LINE_START_MS
    return shortTimedTitle && normalizedText == context.title
}

private fun parseLyricSanitizeLine(index: Int, rawLine: String): LyricSanitizeLine {
    val trimmed = rawLine.trim()
    val yrcHeader = lyricYrcLineHeaderRegex.find(trimmed)
    val lrcTimestamp = lyricLrcTimestampWithGroupsRegex.find(trimmed)
    val startMs = yrcHeader?.groupValues?.getOrNull(1)?.toLongOrNull()
        ?: lrcTimestamp?.let(::parseLyricLrcTimestampMs)
    val durationMs = yrcHeader?.groupValues?.getOrNull(2)?.toLongOrNull()
    val text = trimmed
        .replace(lyricYrcLineHeaderRegex, "")
        .replace(lyricWordTimingRegex, "")
        .replace(lyricLrcTimestampRegex, "")
        .trim()
    return LyricSanitizeLine(
        index = index,
        rawLine = rawLine,
        text = text,
        startMs = startMs,
        durationMs = durationMs,
    )
}

private fun parseLyricLrcTimestampMs(match: MatchResult): Long? {
    val minutes = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
    val seconds = match.groupValues.getOrNull(2)?.toLongOrNull() ?: return null
    val fraction = match.groupValues.getOrNull(3).orEmpty()
    val millis = when (fraction.length) {
        0 -> 0L
        1 -> fraction.toLongOrNull()?.times(100L)
        2 -> fraction.toLongOrNull()?.times(10L)
        else -> fraction.take(3).toLongOrNull()
    } ?: return null
    return minutes * 60_000L + seconds * 1_000L + millis
}

private fun splitLyricSanitizeArtists(value: String): List<String> {
    val normalizedArtist = normalizeLyricText(value)
    return (listOf(normalizedArtist) + lyricSanitizeArtistSeparatorRegex.split(value)
        .map(::normalizeLyricText))
        .filter { it.isNotBlank() }
        .distinct()
}

private fun containsSanitizePhrase(text: String, phrase: String): Boolean {
    if (text.isBlank() || phrase.isBlank()) {
        return false
    }
    if (text == phrase || text.startsWith("$phrase ") || text.endsWith(" $phrase") || text.contains(" $phrase ")) {
        return true
    }
    return phrase.any { it.code > 127 } && text.contains(phrase)
}

private fun removeLyricLines(lines: List<String>, removalIndexes: Set<Int>): String {
    if (removalIndexes.isEmpty()) {
        return lines.joinToString("\n")
    }
    return lines
        .filterIndexed { index, _ -> index !in removalIndexes }
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString("\n")
}

private fun isEdgeMetadataLine(index: Int, lineCount: Int): Boolean {
    return index < LEADING_METADATA_SCAN_LINES || index >= lineCount - TRAILING_METADATA_SCAN_LINES
}

private fun normalizeLyricLineBreaks(value: String): String {
    return value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
}

private fun looksLikeTtmlLyrics(value: String): Boolean {
    return value.trimStart().startsWith("<") &&
        value.contains(Regex("""<\s*tt(?:\s|>)""", RegexOption.IGNORE_CASE))
}

private const val LEADING_METADATA_SCAN_LINES = 8
private const val TRAILING_METADATA_SCAN_LINES = 4
private const val SHORT_IDENTITY_LINE_DURATION_MS = 2_500L
private const val EDGE_IDENTITY_LINE_START_MS = 12_000L
private const val TRANSLATION_PAIR_TIMESTAMP_DRIFT_MS = 350L

private val lyricYrcLineHeaderRegex = Regex("""^\[(\d+),\s*(\d+)]""")
private val lyricWordTimingRegex = Regex("""[<(]\d+,\s*\d+,\s*[-\d]+[>)]""")
private val lyricLrcTimestampRegex = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")
private val lyricLrcTimestampWithGroupsRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val lyricLrcMetadataRegex = Regex(
    """^\[(?:ar|ti|al|by|offset|length|tool|re|ve):[^\]]*]\s*$""",
    RegexOption.IGNORE_CASE,
)
private val lyricEnglishCreditRegex = Regex(
    """^\s*(?:lyrics?|words?|written|composed|arranged|produced|performed|sung|music)\s+by\s*[:：-]?\s*.+$|^\s*(?:lyrics?|composer|lyricist|producer|arranger|vocal(?:s)?|artist|singer|title|album)\s*[:：-]\s*.+$""",
    RegexOption.IGNORE_CASE,
)
private val lyricChineseCreditRegex = Regex(
    """^\s*(?:作词|作詞|填词|填詞|作曲|编曲|編曲|制作人?|演唱|歌手|词曲|詞曲|词|詞|曲|和声|和聲|混音|母带|母帶|录音|錄音|监制|監製|原唱|翻唱|出品|OP|SP)\s*[:：/／-]\s*.+$""",
    RegexOption.IGNORE_CASE,
)
private val lyricEdgeCreditPrefixes = listOf(
    "lyrics by",
    "lyric by",
    "written by",
    "composed by",
    "arranged by",
    "produced by",
    "performed by",
    "music by",
    "words by",
    "作词",
    "作詞",
    "填词",
    "填詞",
    "作曲",
    "编曲",
    "編曲",
    "制作",
    "制作人",
    "演唱",
    "歌手",
    "词曲",
    "詞曲",
)
private val lyricSanitizeArtistSeparatorRegex = Regex(
    "[/,，、&+]|\\b(?:feat\\.?|ft\\.?|featuring)\\b|\\s+[xX]\\s+",
    RegexOption.IGNORE_CASE,
)

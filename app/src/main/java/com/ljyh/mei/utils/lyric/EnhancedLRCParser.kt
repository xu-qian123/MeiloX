package com.ljyh.mei.utils.lyric

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.parser.ILyricsParser
import com.mocharealm.accompanist.lyrics.core.utils.LrcMetadataHelper

/**
 * Enhanced LRC / word-level LRC:
 *
 * ```
 * [03:33.62]<03:33.62>是<03:33.80>怎<03:34.13>么<03:34.40>得<03:34.60>到
 * ```
 *
 * The line carries a classic `[mm:ss.xx]` timestamp and every word is prefixed
 * with its own `<mm:ss.xx>` tag. It is common in LRCLIB and third-party lyric
 * dumps; MeiloX used to keep the word tags as literal text.
 *
 * Produces [KaraokeLine] syllables so the existing renderer highlights per
 * word; a plain line inside an enhanced file becomes a single-syllable line.
 */
object EnhancedLRCParser : ILyricsParser {

    private val lineTimestampRegex = Regex("""^\s*\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTagRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")

    private const val DEFAULT_LINE_DURATION_MS = 5_000
    private const val DEFAULT_TAIL_DURATION_MS = 500

    fun parse(enhancedLrc: String, translationLrc: String?, romanizationLrc: String? = null): SyncedLyrics {
        val parsedLines = LrcMetadataHelper.removeAttributes(enhancedLrc.lines())
            .mapNotNull(::parseLine)
            .sortedBy { it.lineStart }
        val builtLines = parsedLines.mapIndexed { index, line ->
            buildKaraokeLine(line, parsedLines.getOrNull(index + 1)?.lineStart)
        }
        val mergedLines = TranslationHelper.merge(builtLines, translationLrc)
        return SyncedLyrics(lines = mergedLines.withRomanization(romanizationLrc))
    }

    override fun canParse(content: String): Boolean = content.lineSequence().any { line ->
        val trimmed = line.trim()
        lineTimestampRegex.containsMatchIn(trimmed) && wordTagRegex.containsMatchIn(trimmed)
    }

    override fun parse(lines: List<String>): SyncedLyrics {
        val mainLines = lines.filter { lineTimestampRegex.containsMatchIn(it) }
        return parse(mainLines.joinToString("\n"), null)
    }

    override fun parse(content: String): SyncedLyrics = parse(content, null)

    private data class ParsedLine(
        val lineStart: Int,
        val words: List<ParsedWord>,
        val text: String,
    )

    private data class ParsedWord(val start: Int, val text: String)

    private fun parseLine(raw: String): ParsedLine? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val lineMatch = lineTimestampRegex.find(trimmed) ?: return null
        val lineStart = lineMatch.toTimeMs()
        val content = trimmed.substring(lineMatch.range.last + 1)

        val words = mutableListOf<ParsedWord>()
        var pendingPrefix = StringBuilder()
        var cursor = 0
        while (true) {
            val tag = wordTagRegex.find(content, cursor) ?: break
            val chunk = content.substring(cursor, tag.range.first)
            if (words.isEmpty()) {
                pendingPrefix.append(chunk)
            } else {
                words[words.lastIndex] = words.last().copy(text = words.last().text + chunk)
            }
            words.add(ParsedWord(start = tag.toTimeMs(), text = ""))
            cursor = tag.range.last + 1
        }
        if (words.isNotEmpty()) {
            val trailing = content.substring(cursor)
            words[words.lastIndex] = words.last().copy(text = words.last().text + trailing)
            if (pendingPrefix.isNotEmpty()) {
                words[0] = words[0].copy(text = pendingPrefix.toString() + words[0].text)
            }
        }

        val plainText = words.joinToString("") { it.text }.ifEmpty { content }
        return ParsedLine(lineStart = lineStart, words = words, text = plainText)
    }

    private fun buildKaraokeLine(line: ParsedLine, nextLineStart: Int?): KaraokeLine.MainKaraokeLine {
        val words = line.words
        if (words.isEmpty()) {
            val end = (nextLineStart ?: (line.lineStart + DEFAULT_LINE_DURATION_MS))
                .takeIf { it > line.lineStart }
                ?: (line.lineStart + DEFAULT_LINE_DURATION_MS)
            return KaraokeLine.MainKaraokeLine(
                syllables = listOf(KaraokeSyllable(line.text, line.lineStart, end)),
                translation = null,
                alignment = KaraokeAlignment.Unspecified,
                start = line.lineStart,
                end = end,
            )
        }

        val syllables = words.mapIndexed { index, word ->
            val start = word.start.coerceAtLeast(line.lineStart)
            val end = when {
                index < words.lastIndex -> words[index + 1].start
                nextLineStart != null && nextLineStart > start -> nextLineStart
                else -> start + DEFAULT_TAIL_DURATION_MS
            }
            KaraokeSyllable(word.text, start, end.coerceAtLeast(start + 1))
        }
        return KaraokeLine.MainKaraokeLine(
            syllables = syllables,
            translation = null,
            alignment = KaraokeAlignment.Unspecified,
            start = syllables.first().start,
            end = syllables.last().end,
        )
    }

    private fun MatchResult.toTimeMs(): Int {
        val minutes = groupValues.getOrNull(1)?.toIntOrNull() ?: 0
        val seconds = groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val fraction = groupValues.getOrNull(3).orEmpty()
        val millis = when (fraction.length) {
            0 -> 0
            1 -> fraction.toIntOrNull()?.times(100) ?: 0
            2 -> fraction.toIntOrNull()?.times(10) ?: 0
            else -> fraction.take(3).toIntOrNull() ?: 0
        }
        return minutes * 60_000 + seconds * 1_000 + millis
    }
}

package com.ljyh.mei.utils.lyric.edit

import com.ljyh.mei.ui.model.LyricData
import com.ljyh.mei.ui.model.LyricSource
import com.ljyh.mei.utils.lyric.EnhancedLRCParser
import com.ljyh.mei.utils.lyric.LRCParser
import com.ljyh.mei.utils.lyric.QRCParser
import com.ljyh.mei.utils.lyric.TTMLParser
import com.ljyh.mei.utils.lyric.YRCParser
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine

/**
 * Text format for raw lyric strings.
 *
 * The migration intentionally keeps the matching/editing pipeline
 * string-based: detection uses cheap regexes, and acceptance parses through
 * the same MeiloX parsers that render the lyrics.
 */
enum class LyricTextFormat { TTML, YRC, QRC, ENHANCED_LRC, LRC, PLAIN }

object LyricFormatDetector {

    private val ttmlTagRegex = Regex("""<\s*tt(?:\s|>)""", RegexOption.IGNORE_CASE)
    private val lineHeaderRegex = Regex("""^\s*\[\d+\s*,\s*\d+]""", RegexOption.MULTILINE)

    /** YRC word tags look like `(offset,dur,flag)` placed before the text. */
    private val yrcWordRegex = Regex("""\(\d+\s*,\s*\d+\s*,\s*-?\d+\)""")

    /** QRC word tags look like `text(start,dur)` placed after the text. */
    private val qrcWordRegex = Regex("""[^\s\[\]()]+\(\d+\s*,\s*\d+\)""")

    private val lrcTimestampRegex = Regex("""\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]""")

    /** Enhanced LRC word tags look like `<mm:ss.xx>` right after the line timestamp. */
    private val enhancedLrcLineRegex = Regex(
        """\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]\s*<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""",
    )

    fun detect(raw: String): LyricTextFormat {
        val text = raw.trim()
        if (text.isEmpty()) return LyricTextFormat.PLAIN
        if (text.contains(ttmlTagRegex)) return LyricTextFormat.TTML
        if (yrcWordRegex.containsMatchIn(text)) return LyricTextFormat.YRC
        val hasLineHeader = lineHeaderRegex.containsMatchIn(text)
        if (hasLineHeader && qrcWordRegex.containsMatchIn(text)) return LyricTextFormat.QRC
        // `[start,dur]` headers without word tags are parsed by the QRC parser.
        if (hasLineHeader) return LyricTextFormat.QRC
        if (enhancedLrcLineRegex.containsMatchIn(text)) return LyricTextFormat.ENHANCED_LRC
        if (lrcTimestampRegex.containsMatchIn(text)) return LyricTextFormat.LRC
        return LyricTextFormat.PLAIN
    }

    /** True when the raw text carries per-word timings (YRC / QRC / enhanced-LRC / word-level TTML). */
    fun hasWordTiming(raw: String): Boolean = when (detect(raw)) {
        LyricTextFormat.YRC, LyricTextFormat.QRC, LyricTextFormat.ENHANCED_LRC -> true
        LyricTextFormat.TTML -> runCatching {
            TTMLParser().parse(raw).lines.any { line ->
                line is KaraokeLine && line.syllables.size > 1
            }
        }.getOrDefault(false)

        else -> false
    }

    /** True when the text has at least two distinct timestamps (not collapsed to a single point). */
    fun isUsableTimeline(raw: String): Boolean {
        val stamps = buildSet {
            lrcTimestampRegex.findAll(raw).forEach { add(it.value) }
            lineHeaderRegex.findAll(raw).forEach { add(it.value.trim()) }
        }
        return stamps.size >= 2
    }

    /**
     * Parses the raw text with the MeiloX parsers and wraps it as a custom
     * [LyricData]. Returns null when the text cannot produce any line.
     */
    fun parseForDisplay(
        raw: String,
        translatedLyrics: String?,
        durationMs: Long = 0L,
        romanizationLyrics: String? = null,
    ): LyricData? {
        if (raw.isBlank()) return null
        val normalized = normalizeLegacyLrcTimestamps(raw)
        val translation = translatedLyrics?.takeIf { it.isNotBlank() }
            ?.let(::normalizeLegacyLrcTimestamps)

        val format = detect(normalized)
        val parsed = runCatching {
            when (format) {
                LyricTextFormat.TTML -> TTMLParser().parse(normalized)
                LyricTextFormat.YRC -> YRCParser.parse(normalized, translation, romanizationLyrics)
                LyricTextFormat.QRC -> QRCParser.parse(normalized, translation, romanizationLyrics)
                LyricTextFormat.ENHANCED_LRC -> EnhancedLRCParser.parse(normalized, translation, romanizationLyrics)
                LyricTextFormat.LRC -> LRCParser.parse(normalized, translation, romanizationLyrics)
                LyricTextFormat.PLAIN -> LRCParser.parse(
                    plainLyricsToPseudoLrc(normalized, durationMs),
                    translation,
                    romanizationLyrics,
                )
            }
        }.getOrNull() ?: return null

        if (parsed.lines.isEmpty()) return null
        return LyricData(
            isVerbatim = format == LyricTextFormat.TTML ||
                format == LyricTextFormat.YRC ||
                format == LyricTextFormat.QRC ||
                format == LyricTextFormat.ENHANCED_LRC,
            isPureMusic = false,
            source = LyricSource.Custom,
            lyricLine = parsed,
            rawLyrics = normalized,
            rawTranslation = translation,
        )
    }
}

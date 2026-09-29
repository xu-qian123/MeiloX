package com.ljyh.mei.utils.lyric.match

/**
 * Detects "pseudo-synchronized" lyrics whose timestamps collapse to a single
 * point. Ported from NeriPlayer's `LyricTimelinePolicy`, string-only.
 */
object LyricTimelinePolicy {

    private val lrcTimestampRegex = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")
    private val lineHeaderRegex = Regex("""^\s*\[\d+\s*,\s*\d+]""", RegexOption.MULTILINE)

    fun hasLrcTimestamp(raw: String): Boolean =
        lrcTimestampRegex.containsMatchIn(raw) || lineHeaderRegex.containsMatchIn(raw)

    fun distinctTimelineStamps(raw: String): Set<String> = buildSet {
        lrcTimestampRegex.findAll(raw).forEach { add(it.value) }
        lineHeaderRegex.findAll(raw).forEach { add(it.value.trim()) }
    }

    /** ≥3 lines but fewer than two distinct timestamps: treat as plain text. */
    fun hasCollapsedTimedLyricTimeline(raw: String): Boolean {
        val lineCount = raw.lines().count { it.isNotBlank() }
        if (lineCount < 3) return false
        return distinctTimelineStamps(raw).size < 2
    }

    fun isUsableTimedLyricTimeline(raw: String): Boolean {
        if (raw.isBlank()) return false
        if (!hasLrcTimestamp(raw)) return false
        return !hasCollapsedTimedLyricTimeline(raw)
    }
}

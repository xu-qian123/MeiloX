package com.ljyh.mei.utils.lyric.edit

/**
 * Normalizes legacy LRC timestamps of the form `[mm:ss:xx]` (colon before the
 * fraction) into the canonical `[mm:ss.xx]` form understood by the parsers.
 *
 * Ported from NeriPlayer's `normalizeLegacyLrcTimestamps`.
 */
private val legacyLrcTimestampRegex = Regex("""\[(\d{1,2}):(\d{2}):(\d{2,3})]""")

fun normalizeLegacyLrcTimestamps(content: String): String =
    legacyLrcTimestampRegex.replace(content) { match ->
        val minutes = match.groupValues[1].padStart(2, '0')
        val seconds = match.groupValues[2]
        val fraction = match.groupValues[3]
        "[$minutes:$seconds.$fraction]"
    }

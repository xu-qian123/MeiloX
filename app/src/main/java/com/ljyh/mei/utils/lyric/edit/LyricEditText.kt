package com.ljyh.mei.utils.lyric.edit

import com.ljyh.mei.ui.model.LyricData
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine

/**
 * Text helpers for the lyrics editor.
 *
 * The editor always works on raw text: custom lyrics keep their original
 * string, fetched lyrics expose `rawLyrics` from the manager. Re-serializing
 * parsed lines is only a fallback.
 */

fun resolveLyricsEditorInitialText(customLyric: String?, displayed: LyricData): String =
    customLyric?.takeIf { it.isNotBlank() } ?: toEditableLyricsText(displayed)

fun toEditableLyricsText(data: LyricData): String {
    data.rawLyrics?.takeIf { it.isNotBlank() }?.let {
        return normalizeLegacyLrcTimestamps(it)
    }
    return data.lyricLine.lines
        .joinToString("\n") { it.toEditableLrcLine() }
        .trim()
}

fun hasWordTimedLines(data: LyricData): Boolean =
    data.lyricLine.lines.any { line -> line is KaraokeLine && line.syllables.size > 1 }

/**
 * `[mm:ss.xx]` pseudo timestamps evenly distributed over the whole duration,
 * used when the user pastes plain (untimed) text.
 *
 * Ported from NeriPlayer's `convertPlainLyricsToEntries`.
 */
fun plainLyricsToPseudoLrc(plain: String, durationMs: Long): String {
    val lines = plain.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return plain
    val totalMs = if (durationMs > 0L) durationMs else lines.size * 4_000L
    val intervalMs = (totalMs / lines.size).coerceAtLeast(1_000L)
    return lines.mapIndexed { index, line ->
        val startMs = index * intervalMs
        "[%02d:%02d.%03d]%s".format(
            startMs / 60_000L,
            startMs / 1_000L % 60L,
            startMs % 1_000L,
            line,
        )
    }.joinToString("\n")
}

private fun ISyncedLine.toEditableLrcLine(): String {
    val text = when (this) {
        is KaraokeLine -> syllables.joinToString("") { it.content }
        is SyncedLine -> content
        else -> ""
    }
    val startMs = start.toLong().coerceAtLeast(0L)
    return "[%02d:%02d.%03d]%s".format(
        startMs / 60_000L,
        startMs / 1_000L % 60L,
        startMs % 1_000L,
        text,
    )
}

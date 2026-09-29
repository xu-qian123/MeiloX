package com.ljyh.mei.utils.lyric.edit

import kotlin.math.roundToLong

/**
 * Per-song lyric display offset.
 *
 * Unlike NeriPlayer, MeiloX does not apply platform default offsets (its
 * parsers already align with the audio timeline); only the user offset is
 * used, keeping existing playback behavior unchanged for every track.
 */
const val MIN_LYRIC_OFFSET_MS = -5_000L
const val MAX_LYRIC_OFFSET_MS = 5_000L
const val LYRIC_OFFSET_STEP_MS = 50L

/** Snaps to the 50 ms step and clamps to the ±5 s range. */
fun normalizeLyricOffsetMs(value: Long): Long {
    val stepAligned =
        (value.toDouble() / LYRIC_OFFSET_STEP_MS).roundToLong() * LYRIC_OFFSET_STEP_MS
    return stepAligned.coerceIn(MIN_LYRIC_OFFSET_MS, MAX_LYRIC_OFFSET_MS)
}

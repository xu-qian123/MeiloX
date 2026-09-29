package com.ljyh.mei.utils.lyric.match

import kotlin.math.abs

/**
 * Duration tolerance for external lyric candidates.
 * `max(7s, expected * 6%)`, capped at 15s. Ported from NeriPlayer.
 */
fun isExternalLyricDurationCompatible(
    expectedDurationMs: Long,
    candidateDurationMs: Long,
): Boolean {
    if (expectedDurationMs <= 0L || candidateDurationMs <= 0L) return true
    val deltaMs = abs(expectedDurationMs - candidateDurationMs)
    val toleranceMs = maxOf(7_000L, (expectedDurationMs * 6L) / 100L).coerceAtMost(15_000L)
    return deltaMs <= toleranceMs
}

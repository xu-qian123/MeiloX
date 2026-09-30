package com.ljyh.mei.ui.component.player.component

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine

/**
 * 拖动进度条预览歌词时，按行切换给出轻触反馈
 * （交互语义移植自 NeriPlayer 的 LyricSeekHapticFeedback）。
 */
private const val NO_LYRIC_LINE_INDEX = -1
private const val MIN_LYRIC_SEEK_HAPTIC_INTERVAL_MS = 55L

@Composable
fun rememberLyricSeekHapticFeedback(
    lyrics: List<ISyncedLine>,
    lyricOffsetMs: Long = 0L,
): LyricSeekHapticFeedback {
    val hapticFeedback = LocalHapticFeedback.current
    return remember(hapticFeedback, lyrics, lyricOffsetMs) {
        LyricSeekHapticFeedback(
            performTick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            },
            lyrics = lyrics,
            lyricOffsetMs = lyricOffsetMs,
        )
    }
}

class LyricSeekHapticFeedback internal constructor(
    private val performTick: () -> Unit,
    private val lyrics: List<ISyncedLine>,
    private val lyricOffsetMs: Long,
) {
    private var hasBaseline = false
    private var lastLineIndex = NO_LYRIC_LINE_INDEX
    private var lastFeedbackUptimeMs = 0L

    fun onSeekStart(positionMs: Long) {
        hasBaseline = true
        lastLineIndex = resolveLineIndex(positionMs)
        lastFeedbackUptimeMs = 0L
    }

    fun onSeekMove(positionMs: Long) {
        val lineIndex = resolveLineIndex(positionMs)
        if (!hasBaseline) {
            hasBaseline = true
            lastLineIndex = lineIndex
            return
        }

        if (lineIndex == lastLineIndex) return

        lastLineIndex = lineIndex
        if (lineIndex == NO_LYRIC_LINE_INDEX) return

        val now = SystemClock.uptimeMillis()
        if (now - lastFeedbackUptimeMs < MIN_LYRIC_SEEK_HAPTIC_INTERVAL_MS) return

        lastFeedbackUptimeMs = now
        performTick()
    }

    fun onSeekEnd() {
        hasBaseline = false
        lastLineIndex = NO_LYRIC_LINE_INDEX
        lastFeedbackUptimeMs = 0L
    }

    private fun resolveLineIndex(positionMs: Long): Int {
        if (lyrics.isEmpty()) return NO_LYRIC_LINE_INDEX

        val lyricTimeMs = (positionMs + lyricOffsetMs).coerceAtLeast(0L)
        if (lyricTimeMs < lyrics.first().start) return NO_LYRIC_LINE_INDEX

        val lineIndex = lyricFocusLineIndex(lyrics, lyricTimeMs.toInt())
        return if (lineIndex in lyrics.indices) lineIndex else NO_LYRIC_LINE_INDEX
    }
}

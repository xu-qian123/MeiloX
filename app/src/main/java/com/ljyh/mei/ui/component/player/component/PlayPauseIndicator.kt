package com.ljyh.mei.ui.component.player.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.glass.SfSymbol
import kotlinx.coroutines.delay

/** 短缓冲不闪等待圈：超过 1 秒才进入等待状态（与 NeriPlayer 的视觉延迟一致）。 */
private const val PlaybackWaitingVisualDelayMs = 1_000L

@Composable
internal fun rememberDelayedPlaybackWaiting(
    isPlaybackWaiting: Boolean,
    delayMillis: Long = PlaybackWaitingVisualDelayMs,
): Boolean {
    var showWaiting by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaybackWaiting, delayMillis) {
        if (!isPlaybackWaiting) {
            showWaiting = false
            return@LaunchedEffect
        }
        showWaiting = false
        delay(delayMillis)
        showWaiting = true
    }
    return showWaiting
}

internal enum class PlayPauseVisualState {
    Play,
    Pause,
    Ended,
    Waiting,
}

internal fun resolvePlayPauseVisualState(
    playbackState: Int,
    isPlaying: Boolean,
    isPlaybackWaiting: Boolean,
): PlayPauseVisualState = when {
    playbackState == Player.STATE_ENDED -> PlayPauseVisualState.Ended
    isPlaybackWaiting -> PlayPauseVisualState.Waiting
    isPlaying -> PlayPauseVisualState.Pause
    else -> PlayPauseVisualState.Play
}

/**
 * 播放/暂停指示器：NeriPlayer 同款缩放 + 淡入切换；
 * 缓冲超过 1 秒显示等待圈，播完显示重播图标。
 */
@Composable
fun PlayPauseIndicator(
    isPlaying: Boolean,
    playbackState: Int,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    iconSize: Dp = 48.dp,
    iconWeight: FontWeight = FontWeight.Normal,
    waitingIndicatorSize: Dp = 40.dp,
    waitingStrokeWidth: Dp = 3.dp,
    contentDescription: String? = null,
) {
    val isPlaybackWaiting = playbackState == Player.STATE_BUFFERING
    val delayedPlaybackWaiting = rememberDelayedPlaybackWaiting(isPlaybackWaiting)
    val visualState = resolvePlayPauseVisualState(
        playbackState = playbackState,
        isPlaying = isPlaying,
        isPlaybackWaiting = delayedPlaybackWaiting,
    )

    AnimatedContent(
        targetState = visualState,
        modifier = modifier,
        transitionSpec = {
            (scaleIn(initialScale = 0f) + fadeIn()) togetherWith
                (scaleOut(targetScale = 0f) + fadeOut())
        },
        label = "PlayPauseIndicator",
    ) { state ->
        when (state) {
            PlayPauseVisualState.Play -> SfIcon(
                symbol = SfSymbol.PlayFilled,
                contentDescription = contentDescription,
                tint = tint,
                size = iconSize,
                weight = iconWeight,
            )

            PlayPauseVisualState.Pause -> SfIcon(
                symbol = SfSymbol.PauseFilled,
                contentDescription = contentDescription,
                tint = tint,
                size = iconSize,
                weight = iconWeight,
            )

            PlayPauseVisualState.Ended -> SfIcon(
                symbol = SfSymbol.ArrowClockwise,
                contentDescription = contentDescription,
                tint = tint,
                size = iconSize,
                weight = iconWeight,
            )

            PlayPauseVisualState.Waiting -> CircularProgressIndicator(
                modifier = Modifier.size(waitingIndicatorSize),
                color = tint,
                strokeWidth = waitingStrokeWidth,
            )
        }
    }
}

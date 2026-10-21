package com.ljyh.mei.ui.component.player.component

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.ljyh.mei.constants.PlayerHorizontalPadding
import com.ljyh.mei.constants.ProgressBarStyle
import com.ljyh.mei.constants.ProgressBarStyleKey
import com.ljyh.mei.playback.PlayerConnection
import com.ljyh.mei.utils.rememberEnumPreference

@OptIn(UnstableApi::class)
@Composable
fun PlayerControlsSection(
    position: () -> Long,
    duration: () -> Long,
    isPlaying: Boolean,
    playbackState: Int,
    playerConnection: PlayerConnection,
    onLyricClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCommentClick: () -> Unit,
    onMoreClick: () -> Unit,
    isCompact: Boolean = false, // 新增参数
    previewPositionMs: Long? = null,
    onSeekPreviewStart: ((Long) -> Unit)? = null,
    onSeekPreviewMove: ((Long) -> Unit)? = null,
    onSeekPreviewEnd: ((Long?) -> Unit)? = null,
) {
    // 紧凑模式下间距减半
    val spacerHeight = if (isCompact) 8.dp else 24.dp

    val isFM = playerConnection.isFMMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        PlayerProgressSection(
            position, duration, isPlaying, playbackState, playerConnection,
            previewPositionMs, onSeekPreviewStart, onSeekPreviewMove, onSeekPreviewEnd,
        )

        Spacer(Modifier.height(spacerHeight))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding)
        ) {
            PlayerControls(
                playerConnection = playerConnection,
                canSkipPrevious = !isFM.value || playerConnection.player.hasPreviousMediaItem(),
                canSkipNext = playerConnection.player.hasNextMediaItem(),
                isPlaying = isPlaying,
                playbackState = playbackState,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 紧凑模式下可能需要隐藏底部 Toolbar 或者减小间距
        if (!isCompact) {
            Spacer(Modifier.height(spacerHeight))
            PlayerActionToolbar(
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                onLyricClick = onLyricClick,
                onPlaylistClick = onPlaylistClick,
                onSleepTimerClick = onSleepTimerClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onDownloadClick = onDownloadClick,
                onCommentClick = onCommentClick,
                onMoreClick = onMoreClick
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(16.dp))
        } else {
            // 紧凑模式底部留白少一点
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerProgressSection(
    position: () -> Long,
    duration: () -> Long,
    isPlaying: Boolean,
    playbackState: Int,
    playerConnection: PlayerConnection,
    previewPositionMs: Long?,
    onSeekPreviewStart: ((Long) -> Unit)?,
    onSeekPreviewMove: ((Long) -> Unit)?,
    onSeekPreviewEnd: ((Long?) -> Unit)?,
) {
    // Keep ticking position reads inside the progress subtree.
    val progressBarStyle = rememberEnumPreference(ProgressBarStyleKey, ProgressBarStyle.LINEAR).value
    AppleMusicProgressSlider(
        position = previewPositionMs ?: position(),
        duration = duration(),
        isPlaying = isPlaying,
        onPositionChange = { newPosition ->
            playerConnection.player.seekTo(newPosition)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding + 8.dp),
        trackStyle = if (progressBarStyle == ProgressBarStyle.LINEAR) {
            AppleMusicSliderTrackStyle.Solid
        } else {
            AppleMusicSliderTrackStyle.Wave
        },
        isPlaybackWaiting = playbackState == Player.STATE_BUFFERING,
        playbackSpeed = playerConnection.player.playbackParameters.speed,
        playbackSessionKey = playerConnection.player.currentMediaItem?.mediaId,
        previewPositionMs = previewPositionMs,
        onSeekPreviewStart = onSeekPreviewStart,
        onSeekPreviewMove = onSeekPreviewMove,
        onSeekPreviewEnd = onSeekPreviewEnd,
    )

}

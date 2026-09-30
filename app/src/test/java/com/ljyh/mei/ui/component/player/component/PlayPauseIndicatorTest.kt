package com.ljyh.mei.ui.component.player.component

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayPauseIndicatorTest {
    @Test
    fun `ended wins over every other state`() {
        assertEquals(
            PlayPauseVisualState.Ended,
            resolvePlayPauseVisualState(
                playbackState = Player.STATE_ENDED,
                isPlaying = true,
                isPlaybackWaiting = true,
            ),
        )
    }

    @Test
    fun `waiting wins over playing`() {
        assertEquals(
            PlayPauseVisualState.Waiting,
            resolvePlayPauseVisualState(
                playbackState = Player.STATE_BUFFERING,
                isPlaying = true,
                isPlaybackWaiting = true,
            ),
        )
    }

    @Test
    fun `playing shows pause and paused shows play`() {
        assertEquals(
            PlayPauseVisualState.Pause,
            resolvePlayPauseVisualState(
                playbackState = Player.STATE_READY,
                isPlaying = true,
                isPlaybackWaiting = false,
            ),
        )
        assertEquals(
            PlayPauseVisualState.Play,
            resolvePlayPauseVisualState(
                playbackState = Player.STATE_READY,
                isPlaying = false,
                isPlaybackWaiting = false,
            ),
        )
    }

    @Test
    fun `short buffering without the delayed flag stays on the playback icon`() {
        assertEquals(
            PlayPauseVisualState.Pause,
            resolvePlayPauseVisualState(
                playbackState = Player.STATE_BUFFERING,
                isPlaying = true,
                isPlaybackWaiting = false,
            ),
        )
    }
}

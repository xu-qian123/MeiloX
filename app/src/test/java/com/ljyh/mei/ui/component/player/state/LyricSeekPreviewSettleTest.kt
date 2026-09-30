package com.ljyh.mei.ui.component.player.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSeekPreviewSettleTest {
    @Test
    fun `preview releases once playback catches up`() {
        assertTrue(shouldReleaseLyricSeekPreview(playbackPositionMs = 30_000L, pendingSeekPreviewPositionMs = 30_000L))
        assertTrue(shouldReleaseLyricSeekPreview(playbackPositionMs = 30_200L, pendingSeekPreviewPositionMs = 30_000L))
        assertFalse(shouldReleaseLyricSeekPreview(playbackPositionMs = 29_000L, pendingSeekPreviewPositionMs = 30_000L))
    }

    @Test
    fun `preview tolerance is configurable`() {
        assertFalse(
            shouldReleaseLyricSeekPreview(
                playbackPositionMs = 30_200L,
                pendingSeekPreviewPositionMs = 30_000L,
                toleranceMs = 100L,
            )
        )
        assertTrue(
            shouldReleaseLyricSeekPreview(
                playbackPositionMs = 30_050L,
                pendingSeekPreviewPositionMs = 30_000L,
                toleranceMs = 100L,
            )
        )
    }
}

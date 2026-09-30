package com.ljyh.mei.ui.component.player.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进度条纯函数与进度推演器测试（移植自 NeriPlayer 的 AppleMusicSliderTest）。
 */
class AppleMusicSliderTest {
    @Test
    fun `track scale swells while pressed or dragging`() {
        assertEquals(APPLE_SLIDER_REST_TRACK_SCALE, resolveAppleSliderTrackScale(false), 0.0001f)
        assertEquals(APPLE_SLIDER_ACTIVE_TRACK_SCALE, resolveAppleSliderTrackScale(true), 0.0001f)
        assertTrue(
            resolveAppleSliderTrackScale(true) > resolveAppleSliderTrackScale(false)
        )
    }

    @Test
    fun `track inset collapses while pressed or dragging`() {
        assertEquals(
            APPLE_SLIDER_REST_TRACK_INSET_FRACTION,
            resolveAppleSliderTrackInsetFraction(false),
            0.0001f
        )
        assertEquals(
            APPLE_SLIDER_ACTIVE_TRACK_INSET_FRACTION,
            resolveAppleSliderTrackInsetFraction(true),
            0.0001f
        )
        assertTrue(
            resolveAppleSliderTrackInsetFraction(true) <
                resolveAppleSliderTrackInsetFraction(false)
        )
    }

    @Test
    fun `progress normalization clamps invalid values`() {
        assertEquals(0f, normalizeSliderProgress(Float.NaN), 0.0001f)
        assertEquals(0f, normalizeSliderProgress(Float.NEGATIVE_INFINITY), 0.0001f)
        assertEquals(0f, normalizeSliderProgress(-0.5f), 0.0001f)
        assertEquals(1f, normalizeSliderProgress(1.5f), 0.0001f)
        assertEquals(0.5f, normalizeSliderProgress(0.5f), 0.0001f)
    }

    @Test
    fun `touch mapping spans the full track width`() {
        assertEquals(
            0f,
            resolveSliderValueForTouchPosition(touchX = 0f, widthPx = 1_000f),
            0.0001f
        )
        assertEquals(
            0.25f,
            resolveSliderValueForTouchPosition(touchX = 250f, widthPx = 1_000f),
            0.0001f
        )
        assertEquals(
            0.5f,
            resolveSliderValueForTouchPosition(touchX = 500f, widthPx = 1_000f),
            0.0001f
        )
        assertEquals(
            1f,
            resolveSliderValueForTouchPosition(touchX = 1_000f, widthPx = 1_000f),
            0.0001f
        )
        assertEquals(
            1f,
            resolveSliderValueForTouchPosition(touchX = 1_200f, widthPx = 1_000f),
            0.0001f
        )
        assertEquals(
            0f,
            resolveSliderValueForTouchPosition(touchX = 500f, widthPx = 0f),
            0.0001f
        )
    }

    @Test
    fun `short track progress advances at the rendered frame cadence`() {
        assertEquals(
            0.225f,
            resolveSliderProgress(
                anchorValue = 0.20f,
                durationMs = 5_000L,
                playbackSpeed = 1f,
                elapsedNs = 125_000_000L
            ),
            0.0001f
        )
        assertEquals(
            0.25f,
            resolveSliderProgress(
                anchorValue = 0.20f,
                durationMs = 5_000L,
                playbackSpeed = 1f,
                elapsedNs = 250_000_000L
            ),
            0.0001f
        )
    }

    @Test
    fun `progress prediction clamps values and ignores invalid inputs`() {
        assertEquals(
            0f,
            resolveSliderProgress(
                anchorValue = -0.2f,
                durationMs = 5_000L,
                playbackSpeed = 1f,
                elapsedNs = -1L
            ),
            0.0001f
        )
        assertEquals(
            1f,
            resolveSliderProgress(
                anchorValue = 0.8f,
                durationMs = 5_000L,
                playbackSpeed = 1f,
                elapsedNs = 5_000_000_000L
            ),
            0.0001f
        )
        assertEquals(
            0.8f,
            resolveSliderProgress(
                anchorValue = 0.8f,
                durationMs = 0L,
                playbackSpeed = 1f,
                elapsedNs = 250_000_000L
            ),
            0.0001f
        )
        assertEquals(
            0.8f,
            resolveSliderProgress(
                anchorValue = 0.8f,
                durationMs = 5_000L,
                playbackSpeed = Float.NaN,
                elapsedNs = 250_000_000L
            ),
            0.0001f
        )
    }

    @Test
    fun `progress prediction stops for waiting and seek previews`() {
        assertTrue(
            shouldPredictSliderProgress(
                isPlaybackAnimating = true,
                isProgressStalled = false,
                isProgressPreviewing = false
            )
        )
        assertFalse(
            shouldPredictSliderProgress(
                isPlaybackAnimating = true,
                isProgressStalled = true,
                isProgressPreviewing = false
            )
        )
        assertFalse(
            shouldPredictSliderProgress(
                isPlaybackAnimating = true,
                isProgressStalled = false,
                isProgressPreviewing = true
            )
        )
        assertFalse(
            shouldPredictSliderProgress(
                isPlaybackAnimating = false,
                isProgressStalled = false,
                isProgressPreviewing = false
            )
        )
    }

    @Test
    fun `predictor reanchors each real progress update without sampler lag`() {
        val predictor = SliderProgressPredictor(0.20f)

        predictor.updateTarget(
            targetValue = 0.20f,
            durationMs = 5_000L,
            playbackSpeed = 1f,
            animate = true
        )
        predictor.onFrame(1_000_000_000L)
        predictor.onFrame(1_125_000_000L)
        assertEquals(0.225f, predictor.currentValue, 0.0001f)

        predictor.updateTarget(
            targetValue = 0.25f,
            durationMs = 5_000L,
            playbackSpeed = 1f,
            animate = true
        )
        predictor.onFrame(1_250_000_000L)
        assertEquals(0.25f, predictor.currentValue, 0.0001f)
        predictor.onFrame(1_375_000_000L)
        assertEquals(0.275f, predictor.currentValue, 0.0001f)
    }

    @Test
    fun `predictor immediately aligns after pause and seek`() {
        val predictor = SliderProgressPredictor(0.40f)

        predictor.updateTarget(
            targetValue = 0.40f,
            durationMs = 10_000L,
            playbackSpeed = 1.5f,
            animate = true
        )
        predictor.onFrame(1_000_000_000L)
        predictor.onFrame(1_200_000_000L)
        assertEquals(0.43f, predictor.currentValue, 0.0001f)

        predictor.updateTarget(
            targetValue = 0.15f,
            durationMs = 10_000L,
            playbackSpeed = 1.5f,
            animate = false
        )
        assertEquals(0.15f, predictor.currentValue, 0.0001f)

        predictor.updateTarget(
            targetValue = 0.15f,
            durationMs = 10_000L,
            playbackSpeed = 1.5f,
            animate = true
        )
        predictor.onFrame(2_000_000_000L)
        assertEquals(0.15f, predictor.currentValue, 0.0001f)
        predictor.onFrame(2_200_000_000L)
        assertEquals(0.18f, predictor.currentValue, 0.0001f)
    }

    @Test
    fun `predictor resets its frame anchor when animation resumes`() {
        val predictor = SliderProgressPredictor(0.40f)

        predictor.updateTarget(
            targetValue = 0.40f,
            durationMs = 10_000L,
            playbackSpeed = 1f,
            animate = true
        )
        predictor.onFrame(1_000_000_000L)
        predictor.onFrame(1_200_000_000L)
        assertEquals(0.42f, predictor.currentValue, 0.0001f)

        predictor.resetFrameAnchor()
        predictor.onFrame(10_000_000_000L)
        assertEquals(0.42f, predictor.currentValue, 0.0001f)
        predictor.onFrame(10_200_000_000L)
        assertEquals(0.44f, predictor.currentValue, 0.0001f)
    }
}

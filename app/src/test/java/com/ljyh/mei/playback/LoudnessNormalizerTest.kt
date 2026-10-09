package com.ljyh.mei.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class LoudnessNormalizerTest {
    private val enabled = LoudnessConfiguration(enabled = true)

    @Test fun kWeightingMatches997HzReferenceAndChannelEnergySum() {
        val mono = measure(48_000, 1)
        assertEquals(-23.0, mono, .12)
        assertEquals(mono + 3.0103, measure(48_000, 2), .01)
        for (rate in listOf(44_100, 96_000, 192_000)) assertEquals(mono, measure(rate, 1), .1)
    }

    @Test fun quietMusicRisesAndLoudMusicFallsWithinLimits() {
        val quiet = LoudnessNormalizer(48_000, 2)
        tone(quiet, .04f, 15)
        assertTrue(quiet.gainDb > 4)
        assertTrue(quiet.gainDb <= 9.00001)
        val loud = LoudnessNormalizer(48_000, 2)
        tone(loud, .9f, 8)
        assertTrue(loud.gainDb < -10)
        assertTrue(loud.gainDb >= -12.00001)
    }

    @Test fun silenceAndLowNoiseDoNotRequestBoost() {
        val processor = LoudnessNormalizer(48_000, 2)
        tone(processor, 0f, 5)
        tone(processor, .0001f, 5)
        assertEquals(0.0, processor.gainDb, .00001)
    }

    @Test fun quietTailDoesNotKeepRaisingGain() {
        val processor = LoudnessNormalizer(48_000, 2)
        tone(processor, .1f, 10)
        val before = processor.gainDb
        tone(processor, .001f, 8)
        assertTrue(processor.gainDb < before + .2)
    }

    @Test fun peakGuardProtectsSuddenLoudSectionAfterBoostAndPreservesStereoBalance() {
        val processor = LoudnessNormalizer(48_000, 2)
        tone(processor, .04f, 12)
        repeat(10_000) {
            val frame = floatArrayOf(2f, -1f) // Represents EQ overshoot BEFORE quantization.
            processor.process(frame, enabled)
            assertTrue(abs(frame[0]) <= .890001f)
            assertEquals(-2f, frame[0] / frame[1], .0001f)
        }
    }

    @Test fun autoMixReservesHeadroomEvenForTwoFullVolumeDecks() {
        val first = LoudnessNormalizer(48_000, 2)
        val second = LoudnessNormalizer(48_000, 2)
        val config = enabled.copy(reserveMixHeadroom = true)
        repeat(1_000) {
            val a = floatArrayOf(1f, 1f)
            val b = floatArrayOf(1f, 1f)
            first.process(a, config); second.process(b, config)
            assertTrue(a[0] + b[0] <= .890001f)
        }
    }

    @Test fun gainRateIsBoundedIndependentlyOfPeakProtection() {
        val processor = LoudnessNormalizer(48_000, 2)
        var previous = 0.0
        val frame = FloatArray(2)
        repeat(48_000 * 5) { i ->
            frame.fill((.7 * sin(2 * PI * 997 * i / 48_000)).toFloat())
            processor.process(frame, enabled)
            val delta = processor.gainDb - previous
            assertTrue(delta >= -8.0 / 48_000 - 1e-9 && delta <= 1.5 / 48_000 + 1e-9)
            previous = processor.gainDb
        }
    }

    @Test fun resetDoesNotCarryBoostIntoNextSong() {
        val processor = LoudnessNormalizer(48_000, 2)
        tone(processor, .04f, 12)
        assertTrue(processor.gainDb > 4)
        processor.reset()
        assertEquals(0.0, processor.gainDb, 0.0)
        assertFalse(processor.measuredLufs.isFinite())
        val frame = floatArrayOf(1f, 1f)
        processor.process(frame, enabled)
        assertTrue(frame[0] <= .890001f)
    }

    @Test fun disablingReturnsToUnityAndInvalidSamplesDoNotPoisonState() {
        val processor = LoudnessNormalizer(48_000, 2)
        processor.process(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY), enabled)
        tone(processor, .04f, 10)
        assertTrue(processor.measuredLufs.isFinite())
        tone(processor, .04f, 30, LoudnessConfiguration.Disabled)
        assertEquals(0.0, processor.gainDb, .001)
        assertFalse(processor.needsProcessing)
    }

    @Test fun unsupportedSurroundIsNotMeasuredAsStereo() {
        val processor = LoudnessNormalizer(48_000, 6)
        val frame = FloatArray(6) { .2f }
        repeat(1000) { processor.process(frame, enabled) }
        assertEquals(.2f, frame[0], 0f)
        assertFalse(processor.measuredLufs.isFinite())
    }

    @Test fun disablingAlsoReleasesPeakProtectionForLoudInput() {
        val processor = LoudnessNormalizer(48_000, 2)
        val frame = floatArrayOf(1f, 1f)
        processor.process(frame, enabled)
        assertTrue(frame[0] < 1f)
        repeat(48_000 * 3) {
            frame.fill(1f)
            processor.process(frame, LoudnessConfiguration.Disabled)
        }
        assertEquals(1f, frame[0], 0f)
        assertFalse(processor.needsProcessing)
    }

    @Test fun startupWaitsForEvidenceAndDecksDoNotShareLearnedGain() {
        val first = LoudnessNormalizer(48_000, 2)
        tone(first, .04f, 2)
        assertEquals(0.0, first.gainDb, 0.0)
        tone(first, .04f, 10)
        val second = LoudnessNormalizer(48_000, 2)
        assertTrue(first.gainDb > 4)
        assertEquals(0.0, second.gainDb, 0.0)
    }

    private fun measure(rate: Int, channels: Int): Double {
        val estimator = StreamingLoudnessEstimator(rate, channels)
        val frame = FloatArray(channels)
        // A -20 dBFS peak 997 Hz mono sine measures approximately -23 LUFS.
        repeat(rate * 4) { i ->
            frame.fill((.1 * sin(2 * PI * 997 * i / rate)).toFloat())
            estimator.consume(frame)
        }
        return estimator.measuredLufs
    }

    private fun tone(processor: LoudnessNormalizer, amplitude: Float, seconds: Int,
                     config: LoudnessConfiguration = enabled) {
        val frame = FloatArray(2)
        repeat(48_000 * seconds) { i ->
            frame.fill((amplitude * sin(2 * PI * 997 * i / 48_000)).toFloat())
            processor.process(frame, config)
        }
    }
}

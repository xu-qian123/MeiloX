package com.ljyh.mei.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

data class LoudnessConfiguration(
    val enabled: Boolean = false,
    val targetLufs: Float = -16f,
    val reserveMixHeadroom: Boolean = false,
) {
    companion object { val Disabled = LoudnessConfiguration() }
}

/** BS.1770 K weighting, 400 ms blocks at 100 ms intervals, with a bounded 30 s history.
 * This is an adaptive estimate, NOT whole-programme integrated loudness certification.
 * Mono/stereo only: other channel layouts need explicit channel maps and LFE exclusion.
 */
internal class StreamingLoudnessEstimator(private val sampleRate: Int, private val channels: Int) {
    private val shelf = WeightingFilter.shelf(sampleRate, channels)
    private val highPass = WeightingFilter.highPass(sampleRate, channels)
    private val quanta = DoubleArray(4)
    private val blocks = DoubleArray(300)
    private var quantumIndex = 0
    private var quantumCount = 0
    private var blockIndex = 0
    private var blockCount = 0
    private var frames = 0
    private var energy = 0.0
    var measuredLufs = Double.NEGATIVE_INFINITY
        private set
    var recentLufs = Double.NEGATIVE_INFINITY
        private set
    var validBlocks = 0
        private set

    fun consume(frame: FloatArray): Boolean {
        for (channel in 0 until channels) {
            val raw = frame[channel]
            val sample = if (raw.isFinite()) raw.toDouble() else 0.0
            val weighted = highPass.process(shelf.process(sample, channel), channel)
            energy += weighted * weighted // Sum channel energies; do not average stereo channels.
        }
        if (++frames < sampleRate / 10) return false
        quanta[quantumIndex] = energy / frames
        quantumIndex = (quantumIndex + 1) % quanta.size
        quantumCount++
        frames = 0
        energy = 0.0
        if (quantumCount < quanta.size) return false
        val block = quanta.sum() / quanta.size
        recentLufs = lufs(block)
        blocks[blockIndex] = block
        blockIndex = (blockIndex + 1) % blocks.size
        blockCount = min(blockCount + 1, blocks.size)
        var sum = 0.0
        var count = 0
        for (i in 0 until blockCount) {
            if (blocks[i] >= ABSOLUTE_GATE) { sum += blocks[i]; count++ }
        }
        val relativeGate = if (count > 0) max(ABSOLUTE_GATE, sum / count / 10.0) else ABSOLUTE_GATE
        sum = 0.0
        count = 0
        for (i in 0 until blockCount) {
            if (blocks[i] >= relativeGate) { sum += blocks[i]; count++ }
        }
        validBlocks = count
        measuredLufs = if (count > 0) lufs(sum / count) else Double.NEGATIVE_INFINITY
        return true
    }

    fun reset() {
        shelf.reset(); highPass.reset()
        quanta.fill(0.0); blocks.fill(0.0)
        quantumIndex = 0; quantumCount = 0; blockIndex = 0; blockCount = 0
        frames = 0; energy = 0.0; validBlocks = 0
        measuredLufs = Double.NEGATIVE_INFINITY; recentLufs = Double.NEGATIVE_INFINITY
    }

    private fun lufs(value: Double) = if (value > 0) -0.691 + 10 * log10(value) else Double.NEGATIVE_INFINITY

    companion object { private val ABSOLUTE_GATE = 10.0.pow((-70.0 + 0.691) / 10.0) }
}

/** Double precision is important for the RLB high-pass at high sample rates. */
private class WeightingFilter(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double, channels: Int,
) {
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)
    fun process(x: Double, channel: Int): Double {
        val y = b0 * x + z1[channel]
        z1[channel] = b1 * x - a1 * y + z2[channel]
        z2[channel] = b2 * x - a2 * y
        return y
    }
    fun reset() { z1.fill(0.0); z2.fill(0.0) }

    companion object {
        // De Man parameterization of the BS.1770 reference filters, recalculated for fs.
        fun shelf(fs: Int, channels: Int): WeightingFilter {
            val k = tan(PI * 1681.974450955533 / fs)
            val q = 0.7071752369554196
            val vh = 10.0.pow(3.999843853973347 / 20)
            val vb = vh.pow(0.4996667741545416)
            val a0 = 1 + k / q + k * k
            return WeightingFilter((vh + vb * k / q + k * k) / a0,
                2 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0,
                2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0, channels)
        }
        fun highPass(fs: Int, channels: Int): WeightingFilter {
            val k = tan(PI * 38.13547087602444 / fs)
            val q = 0.5003270373238773
            val a0 = 1 + k / q + k * k
            // RLB uses an unnormalized numerator, unlike a generic cookbook high-pass.
            return WeightingFilter(1.0, -2.0, 1.0, 2 * (k * k - 1) / a0,
                (1 - k / q + k * k) / a0, channels)
        }
    }
}

/** Per-deck, audio-thread-confined state. Input is post-EQ, before gain or PCM clipping.
 * Stereo-linked sample-peak protection has no lookahead/delay; it is not a true-peak limiter.
 */
internal class LoudnessNormalizer(private val sampleRate: Int, private val channels: Int) {
    private val estimator = StreamingLoudnessEstimator(sampleRate, channels)
    private val fallAlpha = 1 - exp(-1.0 / (sampleRate * .080))
    private val riseAlpha = 1 - exp(-1.0 / (sampleRate * 2.0))
    private val fallRatio = 10.0.pow(-8.0 / (20 * sampleRate))
    private val riseRatio = 10.0.pow(1.5 / (20 * sampleRate))
    private val limiterRelease = 1 - exp(-1.0 / (sampleRate * .150))
    private var gain = 1.0
    private var desired = 1.0
    private var limiter = 1.0
    private var wasEnabled = false
    val gainDb: Double get() = 20 * log10(gain)
    val measuredLufs: Double get() = estimator.measuredLufs
    val needsProcessing: Boolean get() = wasEnabled || gain != 1.0 || limiter != 1.0

    fun process(frame: FloatArray, configuration: LoudnessConfiguration) {
        val enabled = configuration.enabled && channels in 1..2
        if (enabled != wasEnabled) {
            // Enabling starts with fresh evidence; disabling ramps back to unity.
            estimator.reset()
            desired = 1.0
            wasEnabled = enabled
        }
        for (ch in 0 until channels) if (!frame[ch].isFinite()) frame[ch] = 0f
        if (enabled && estimator.consume(frame)) {
            val measured = estimator.measuredLufs
            val recent = estimator.recentLufs
            if (measured.isFinite() && recent > -45.0) {
                val target = configuration.targetLufs.takeIf { it.isFinite() }?.coerceIn(-20f, -10f) ?: -16f
                desired = 10.0.pow((target - measured).coerceIn(-12.0, 9.0) / 20)
                // Require ~3 s of gated evidence before boosting. Do not chase quiet tails.
                if (estimator.validBlocks < 27 || recent < measured - 6) desired = min(desired, gain)
            } else {
                desired = min(desired, gain)
            }
        }
        val alpha = if (desired < gain) fallAlpha else riseAlpha
        gain = (gain + (desired - gain) * alpha).coerceIn(gain * fallRatio, gain * riseRatio)
        if (!enabled && abs(gain - 1) < .00001 && abs(limiter - 1) < .00001) {
            gain = 1.0; limiter = 1.0
            return
        }
        var peak = 0.0
        for (ch in 0 until channels) peak = max(peak, abs(frame[ch].toDouble()) * gain)
        // Two independent AudioTracks can overlap. Reserving half amplitude per deck
        // also covers transient volume-write skew while the AutoMix envelope advances.
        val ceiling = if (configuration.reserveMixHeadroom) .445 else .89
        val safe = if (peak > ceiling) ceiling / peak else 1.0
        val released = limiter + (1 - limiter) * limiterRelease
        limiter = if (enabled) min(safe, released) else released
        val multiplier = (gain * limiter).toFloat()
        for (ch in 0 until channels) frame[ch] *= multiplier
    }

    fun reset() {
        estimator.reset()
        gain = 1.0; desired = 1.0; limiter = 1.0; wasEnabled = false
    }
}

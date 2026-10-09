package com.ljyh.mei.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LoudnessPcmTest {
    private val enabled = LoudnessConfiguration(enabled = true)

    @Test fun pcm16AndFloatProduceComparableOutput() {
        val pcm = render(C.ENCODING_PCM_16BIT)
        val floats = render(C.ENCODING_PCM_FLOAT)
        assertEquals(pcm.size, floats.size)
        for (i in pcm.indices) assertEquals(floats[i], pcm[i], .0002f)
    }

    @Test fun arbitraryByteChunksDoNotLoseFramesOrChangeAudio() {
        val whole = render(C.ENCODING_PCM_FLOAT)
        val split = render(C.ENCODING_PCM_FLOAT, chunkBytes = 137)
        assertArrayEquals(whole, split, 0f)
    }

    @Test fun eqOvershootIsProtectedBeforeIntegerClipping() {
        val output = render(C.ENCODING_PCM_16BIT, amplitude = .9f,
            eq = EqualizerConfiguration(enabled = true, preamp = 6f))
        assertTrue(output.all { abs(it) <= .89001f })
        assertTrue(output.any { abs(it) > .8f })
    }

    @Test fun disabledProcessorPreservesPcmBytesIncludingNegativeFullScale() {
        val processor = processor(C.ENCODING_PCM_16BIT, LoudnessConfiguration.Disabled)
        val bytes = byteArrayOf(0, -128, -1, 127, 1, 0, -1, -1)
        processor.queueInput(ByteBuffer.wrap(bytes))
        val output = processor.output
        val actual = ByteArray(output.remaining())
        output.get(actual)
        assertArrayEquals(bytes, actual)
    }

    @Test fun flushAndStreamDiscontinuityResetExactlyLikeFreshProcessor() {
        for (flush in listOf(true, false)) {
            val used = processor(C.ENCODING_PCM_FLOAT)
            val warmup = tone(C.ENCODING_PCM_FLOAT, .04f, 5)
            used.queueInput(ByteBuffer.wrap(warmup)); used.output
            if (flush) used.flush() else used.resetLoudness()
            val fresh = processor(C.ENCODING_PCM_FLOAT)
            val input = tone(C.ENCODING_PCM_FLOAT, .8f, 1)
            used.queueInput(ByteBuffer.wrap(input))
            fresh.queueInput(ByteBuffer.wrap(input))
            val a = used.output; val b = fresh.output
            assertEquals(a, b)
        }
    }

    @Test fun sampleRateChangeReconfiguresMeter() {
        val processor = processor(C.ENCODING_PCM_FLOAT)
        processor.queueInput(ByteBuffer.wrap(tone(C.ENCODING_PCM_FLOAT, .04f, 4)))
        processor.output
        processor.configure(AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()
        val input = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putFloat(.8f).putFloat(-.8f)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(.8f, output.float, .00001f)
        assertEquals(-.8f, output.float, .00001f)
    }

    private fun processor(encoding: Int, configuration: LoudnessConfiguration = enabled,
                          eq: EqualizerConfiguration = EqualizerConfiguration()): TenBandEqualizerProcessor {
        return TenBandEqualizerProcessor({ eq }, { configuration }).apply {
            configure(AudioProcessor.AudioFormat(48_000, 2, encoding))
            flush()
        }
    }

    private fun render(encoding: Int, chunkBytes: Int = Int.MAX_VALUE, amplitude: Float = .04f,
                       eq: EqualizerConfiguration = EqualizerConfiguration()): FloatArray {
        val processor = processor(encoding, eq = eq)
        val bytes = tone(encoding, amplitude, 5)
        val result = ByteArrayOutputStream(bytes.size)
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(chunkBytes, bytes.size - offset)
            processor.queueInput(ByteBuffer.wrap(bytes, offset, count))
            val out = processor.output
            val chunk = ByteArray(out.remaining())
            out.get(chunk); result.write(chunk)
            offset += count
        }
        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
        val output = ByteBuffer.wrap(result.toByteArray()).order(ByteOrder.nativeOrder())
        return FloatArray(output.remaining() / if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2) {
            if (encoding == C.ENCODING_PCM_FLOAT) output.float else output.short / 32768f
        }
    }

    private fun tone(encoding: Int, amplitude: Float, seconds: Int): ByteArray {
        val result = ByteBuffer.allocate(48_000 * seconds * 2 * if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2)
            .order(ByteOrder.nativeOrder())
        repeat(48_000 * seconds) { i ->
            val value = (amplitude * sin(2 * PI * 997 * i / 48_000)).toFloat()
            repeat(2) {
                if (encoding == C.ENCODING_PCM_FLOAT) result.putFloat(value)
                else result.putShort((value * 32768).toInt().toShort())
            }
        }
        return result.array()
    }
}

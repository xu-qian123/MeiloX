package com.ljyh.mei.data.network.netease

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.luben.zstd.Zstd
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NcblNativeCompressionTest {
    @Test
    fun androidNativeCodecCompressesAndDecompresses() {
        val input = "playback start / next track / playback duration".repeat(100).toByteArray()
        val compressed = compressNcblBody(input)
        assertArrayEquals(input, Zstd.decompress(compressed, input.size))
    }

    @Test
    fun playbackHistoryEnvelopeUsesThePackagedNativeCodec() {
        repeat(20) {
            val encoded = NcblCodec.encode("{}".toByteArray(), "play\u0001{\"id\":123}".toByteArray())
            assertArrayEquals("NCBL".toByteArray(), encoded.copyOfRange(0, 4))
            assertTrue(encoded.size > 80)
        }
    }
}

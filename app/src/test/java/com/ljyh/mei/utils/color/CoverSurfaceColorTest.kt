package com.ljyh.mei.utils.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverSurfaceColorTest {
    private val gray = 0xff888888.toInt()
    private val yellow = 0xffffcc00.toInt()

    @Test fun grayPhotoDoesNotBecomeYellowFromSmallAccents() {
        val pixels = IntArray(100) { gray }
        for (row in 5 until 10) pixels[row * 10] = yellow
        assertEquals(gray, CoverSurfaceColor.extract(pixels, 10, 10))
    }

    @Test fun bottomHalfWinsOverOppositeColorAtTheTop() {
        val pixels = IntArray(100) { if (it < 50) yellow else gray }
        assertEquals(gray, CoverSurfaceColor.extract(pixels, 10, 10))
    }

    @Test fun broadColoredAreaStillKeepsItsHue() {
        val pixels = IntArray(100) { yellow }
        for (row in 5 until 10) pixels[row * 10] = gray
        assertEquals(yellow, CoverSurfaceColor.extract(pixels, 10, 10))
    }

    @Test fun lowerEdgeHasMoreInfluenceThanTheMiddle() {
        val pixels = IntArray(16) { if (it < 12) gray else yellow }
        assertEquals(yellow, CoverSurfaceColor.extract(pixels, 4, 4))
    }

    @Test fun differentNeutralShadesRemainNeutral() {
        val pixels = IntArray(100) { index ->
            val channel = 60 + index
            (0xff shl 24) or (channel shl 16) or (channel shl 8) or channel
        }
        val color = CoverSurfaceColor.extract(pixels, 10, 10)!!
        assertEquals(color ushr 16 and 255, color ushr 8 and 255)
        assertEquals(color ushr 8 and 255, color and 255)
    }

    @Test fun squareCropExcludesUnseenSidesAndBottomRows() {
        val landscape = IntArray(12) { yellow }
        for (y in 0..1) for (x in 2..3) landscape[y * 6 + x] = gray
        assertEquals(gray, CoverSurfaceColor.extract(landscape, 6, 2))
        val portrait = IntArray(12) { yellow }
        for (index in 4..7) portrait[index] = gray
        assertEquals(gray, CoverSurfaceColor.extract(portrait, 2, 6))
    }

    @Test fun blackAndWhiteAreValidColorsRatherThanMissingArtwork() {
        assertEquals(0xff000000.toInt(), CoverSurfaceColor.extract(intArrayOf(0xff000000.toInt()), 1, 1))
        assertEquals(0xffffffff.toInt(), CoverSurfaceColor.extract(intArrayOf(0xffffffff.toInt()), 1, 1))
    }

    @Test fun transparentPixelsDoNotOutvoteOpaqueGray() {
        val pixels = IntArray(16) { 0x00ffff00 }
        pixels[14] = gray
        assertEquals(gray, CoverSurfaceColor.extract(pixels, 4, 4))
        assertNull(CoverSurfaceColor.extract(IntArray(16), 4, 4))
    }

    @Test fun invalidDimensionsHaveNoSeed() {
        assertNull(CoverSurfaceColor.extract(intArrayOf(gray), 0, 1))
        assertNull(CoverSurfaceColor.extract(intArrayOf(gray), 2, 2))
    }
}

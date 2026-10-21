package com.ljyh.mei.utils.color

/** Representative color where a full-width cover fades into the detail page. */
internal object CoverSurfaceColor {
    fun extract(pixels: IntArray, width: Int, height: Int): Int? {
        if (width <= 0 || height <= 0 || width.toLong() * height > pixels.size) return null
        // Keep neutral pixels together instead of discarding them in a theme-color score.
        // Colored pixels share broad hue families so photo noise does not split a large area.
        val weights = LongArray(13)
        val reds = LongArray(13)
        val greens = LongArray(13)
        val blues = LongArray(13)
        // Match the centered square crop actually shown by the hero image.
        val side = minOf(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        val start = top + side / 2
        val end = top + side
        val span = (end - start - 1).coerceAtLeast(1)
        for (y in start until end) {
            val rowWeight = 1L + (y - start) * 2L / span
            for (x in left until left + side) {
                val pixel = pixels[y * width + x]
                val alpha = pixel ushr 24
                if (alpha == 0) continue
                val weight = rowWeight * alpha
                val r = pixel ushr 16 and 255
                val g = pixel ushr 8 and 255
                val b = pixel and 255
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val delta = max - min
                val bucket = if (delta <= 16 || delta <= max * 0.15f) {
                    0
                } else {
                    val hue = when (max) {
                        r -> 60f * (g - b) / delta
                        g -> 120f + 60f * (b - r) / delta
                        else -> 240f + 60f * (r - g) / delta
                    }
                    1 + (((hue + 360f + 15f) % 360f) / 30f).toInt()
                }
                weights[bucket] += weight
                reds[bucket] += r * weight
                greens[bucket] += g * weight
                blues[bucket] += b * weight
            }
        }
        val dominant = weights.indices.maxBy { weights[it] }
        val weight = weights[dominant]
        if (weight == 0L) return null
        return (0xff shl 24) or
            ((reds[dominant] / weight).toInt() shl 16) or
            ((greens[dominant] / weight).toInt() shl 8) or
            (blues[dominant] / weight).toInt()
    }
}

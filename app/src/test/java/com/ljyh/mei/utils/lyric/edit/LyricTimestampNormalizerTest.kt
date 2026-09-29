package com.ljyh.mei.utils.lyric.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricTimestampNormalizerTest {

    @Test
    fun normalizesColonFractionsToMillis() {
        assertEquals("[00:12.345]hello", normalizeLegacyLrcTimestamps("[0:12:345]hello"))
        assertEquals("[03:04.56]hi", normalizeLegacyLrcTimestamps("[3:04:56]hi"))
    }

    @Test
    fun keepsCanonicalTimestampsUntouched() {
        val content = "[00:12.345]hello\n[01:02.00]world"
        assertEquals(content, normalizeLegacyLrcTimestamps(content))
    }

    @Test
    fun ignoresUnrelatedContent() {
        assertEquals("no timestamps here", normalizeLegacyLrcTimestamps("no timestamps here"))
    }
}

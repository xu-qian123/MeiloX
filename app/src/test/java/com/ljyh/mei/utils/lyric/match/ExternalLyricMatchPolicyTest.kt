package com.ljyh.mei.utils.lyric.match

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLyricMatchPolicyTest {

    @Test
    fun smallDeltaIsCompatible() {
        assertTrue(isExternalLyricDurationCompatible(200_000L, 207_000L))
    }

    @Test
    fun largeDeltaIsRejected() {
        assertFalse(isExternalLyricDurationCompatible(200_000L, 215_000L))
    }

    @Test
    fun unknownDurationsPassThrough() {
        assertTrue(isExternalLyricDurationCompatible(0L, 207_000L))
        assertTrue(isExternalLyricDurationCompatible(200_000L, 0L))
    }
}

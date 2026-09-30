package com.ljyh.mei.playback

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaybackCacheTest {
    @Test fun contiguousRealFilesAreRequired() {
        assertTrue(hasCompletePlaybackSpans(10, listOf(span(5, 5), span(0, 5))))
        assertFalse(hasCompletePlaybackSpans(10, listOf(span(0, 5))))
        assertFalse(hasCompletePlaybackSpans(10, listOf(span(0, 4), span(5, 5))))
        assertFalse(hasCompletePlaybackSpans(10, listOf(span(0, 6), span(5, 5))))
        assertFalse(hasCompletePlaybackSpans(10, listOf(PlaybackCacheSpan(0, 10, null))))
        assertFalse(hasCompletePlaybackSpans(10, listOf(PlaybackCacheSpan(0, 10, 9))))
    }

    @Test fun unknownEmptyAndCorruptLengthsAreRejected() {
        assertFalse(hasCompletePlaybackSpans(-1, listOf(span(0, 10))))
        assertFalse(hasCompletePlaybackSpans(0, emptyList()))
        assertFalse(hasCompletePlaybackSpans(10, listOf(span(0, 11))))
        assertFalse(hasCompletePlaybackSpans(10, listOf(span(-1, 11))))
        assertFalse(hasCompletePlaybackSpans(Long.MAX_VALUE, listOf(span(0, 1), span(1, Long.MAX_VALUE))))
    }

    @Test fun cacheSelectionPrefersRequestedThenLowerQualityWithoutMixingSongs() {
        val exact = key("lossless")
        val lower = key("exhigh")
        val higher = key("hires")
        val keys = setOf(higher, lower, exact, playbackCacheKey("1234", "lossless", "a", 10))
        assertEquals(exact, selectCachedPlaybackKey(keys, "123", "lossless", true) { true })
        assertEquals(lower, selectCachedPlaybackKey(keys, "123", "lossless", true) { it != exact })
        assertNull(selectCachedPlaybackKey(keys, "123", "lossless", false) { it != exact })
        assertEquals(higher, selectCachedPlaybackKey(setOf(higher), "123", "standard", true) { true })
        assertNull(selectCachedPlaybackKey(keys, "999", "lossless", true) { true })
    }

    @Test fun sourceRevisionsAndLegacyEntriesAreNotMerged() {
        val old = key("exhigh")
        val newer = playbackCacheKey("123", "exhigh", "b", 10)
        assertEquals(newer, selectCachedPlaybackKey(setOf("123", old, newer), "123", "exhigh", true) { it == newer })
        assertNull(selectCachedPlaybackKey(setOf("123", old, newer), "123", "exhigh", true) { false })
    }

    @Test fun completePreferredCacheNeedsNoNetworkEvenOnline() = runBlocking {
        assertEquals("cache", resolvePlaybackWithCache(true, { "cache" }, { error("fallback") }) { error("network") })
    }

    @Test fun offlineDifferentQualityCacheNeedsNoUrlApi() = runBlocking {
        assertEquals("lower", resolvePlaybackWithCache(false, { null }, { "lower" }) { error("network") })
    }

    @Test fun onlineStillRequestsSelectedQualityBeforeUsingLowerCache() = runBlocking {
        assertEquals("remote", resolvePlaybackWithCache(true, { null }, { error("fallback") }) { "remote" })
    }

    @Test fun networkFailureFallsBackToCompleteCache() = runBlocking {
        assertEquals("lower", resolvePlaybackWithCache(true, { null }, { "lower" }) { throw IOException("offline") })
    }

    @Test fun failureWithoutCacheIsPreserved() = runBlocking {
        val failure = IOException("offline")
        try {
            resolvePlaybackWithCache<String>(false, { null }, { null }) { throw failure }
            fail("Expected the original failure")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
    }

    @Test fun cancellationDoesNotStartFallbackPlayback() = runBlocking {
        try {
            resolvePlaybackWithCache(true, { null }, { error("fallback") }) { throw CancellationException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Cancellation belongs to the playback load that was stopped.
        }
    }

    private fun span(position: Long, length: Long) = PlaybackCacheSpan(position, length, length)
    private fun key(quality: String) = playbackCacheKey("123", quality, "a", 10)
}

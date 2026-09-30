package com.ljyh.mei.utils.lyric

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LyricLoadSessionTest {
    @Test fun `late result from same song before saving cannot overwrite custom lyrics`() = runTest {
        val session = LyricLoadSession()
        val oldRequest = session.begin()
        val resultReady = CompletableDeferred<Unit>()
        var displayed = "custom"
        val request = launch {
            resultReady.await()
            if (session.canApplyRemote(oldRequest)) displayed = "network"
        }
        val reload = session.begin()
        session.setCustomLyrics(reload, true)
        resultReady.complete(Unit)
        request.join()
        assertEquals("custom", displayed)
        assertFalse(session.canApplyRemote(reload))
    }

    @Test fun `reload invalidates results even before persisted text has loaded`() {
        val session = LyricLoadSession()
        val oldRequest = session.begin()
        val reload = session.begin()
        assertFalse(session.canApplyRemote(oldRequest))
        assertTrue(session.canApplyRemote(reload))
    }

    @Test fun `stale custom read cannot change current song ownership`() {
        val session = LyricLoadSession()
        val oldRequest = session.begin()
        val current = session.begin()
        session.setCustomLyrics(current, true)
        session.setCustomLyrics(oldRequest, false)
        assertFalse(session.canApplyRemote(current))
    }

    @Test fun `explicit restore allows new requests but never old ones`() {
        val session = LyricLoadSession()
        val edited = session.begin()
        session.setCustomLyrics(edited, true)
        val restored = session.begin()
        assertTrue(session.canApplyRemote(restored))
        assertFalse(session.canApplyRemote(edited))
    }
}

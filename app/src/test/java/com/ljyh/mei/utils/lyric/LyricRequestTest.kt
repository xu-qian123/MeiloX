package com.ljyh.mei.utils.lyric

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricRequestTest {
    @Test fun selectionSearchCompletesWhileLyricRequestIsStillPending() = runTest {
        val selectionReady = CompletableDeferred<Unit>()
        val sourceStarted = CompletableDeferred<Unit>()
        val load = launch {
            runLyricRequest(100, { true }, {}, selectionSearch = {
                selectionReady.complete(Unit)
            }) {
                sourceStarted.complete(Unit)
                awaitCancellation()
            }
        }
        sourceStarted.await()
        selectionReady.await()
        assertFalse("Selection results must not wait for the lyric deadline", load.isCompleted)
        load.join()
    }

    @Test fun sourceDeadlineDoesNotCancelIndependentSelectionSearch() = runTest {
        val timeout = CompletableDeferred<Unit>()
        var selectionReady = false
        val load = launch {
            runLyricRequest(100, { true }, { timeout.complete(Unit) }, selectionSearch = {
                delay(200)
                selectionReady = true
            }) { awaitCancellation() }
        }
        timeout.await()
        assertFalse(selectionReady)
        load.join()
        assertTrue(selectionReady)
    }

    @Test fun cancellingLoadStopsBothSelectionSearchAndLyricRequest() = runTest {
        val selectionStarted = CompletableDeferred<Unit>()
        val sourceStarted = CompletableDeferred<Unit>()
        var selectionCancelled = false
        var sourceCancelled = false
        var timedOut = false
        val load = launch {
            runLyricRequest(100, { true }, { timedOut = true }, selectionSearch = {
                selectionStarted.complete(Unit)
                try { awaitCancellation() } finally { selectionCancelled = true }
            }) {
                sourceStarted.complete(Unit)
                try { awaitCancellation() } finally { sourceCancelled = true }
            }
        }
        selectionStarted.await()
        sourceStarted.await()
        load.cancel()
        load.join()
        assertTrue(selectionCancelled)
        assertTrue(sourceCancelled)
        assertFalse(timedOut)
    }

    @Test fun sourceDeadlineCancelsTheActualSuspendedRequest() = runTest {
        var requestCancelled = false
        var timedOut = false
        runLyricRequest(100, { true }, { timedOut = true }) {
            try {
                awaitCancellation()
            } finally {
                requestCancelled = true
            }
        }
        assertTrue(requestCancelled)
        assertTrue(timedOut)
    }

    @Test fun outerTimeoutIsNotPublishedAsSourceTimeout() = runTest {
        var timedOut = false
        var cancelled = false
        try {
            withTimeout(20) {
                runLyricRequest(100, { true }, { timedOut = true }) { awaitCancellation() }
            }
        } catch (_: TimeoutCancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(timedOut)
    }

    @Test fun nestedRequestTimeoutIsNotMistakenForOwningDeadline() = runTest {
        var timedOut = false
        var cancelled = false
        try {
            runLyricRequest(100, { true }, { timedOut = true }) {
                withTimeout(20) { awaitCancellation() }
            }
        } catch (_: TimeoutCancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(timedOut)
    }

    @Test fun cancellingLoadDoesNotPublishAnError() = runTest {
        val started = CompletableDeferred<Unit>()
        var timedOut = false
        val load = launch {
            runLyricRequest(100, { true }, { timedOut = true }) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        load.cancel()
        load.join()
        assertFalse(timedOut)
    }

    @Test fun sameSongReloadCannotReceiveThePreviousTimeout() = runTest {
        val session = LyricLoadSession()
        val old = session.begin()
        val started = CompletableDeferred<Unit>()
        var timedOut = false
        val load = launch {
            runLyricRequest(100, { session.canApplyRemote(old) }, { timedOut = true }) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        session.begin()
        load.join()
        assertFalse(timedOut)
    }

    @Test fun savedCustomLyricsRejectPendingSourceTimeout() = runTest {
        val session = LyricLoadSession()
        val token = session.begin()
        var timedOut = false
        runLyricRequest(100, { session.canApplyRemote(token) }, { timedOut = true }) {
            session.setCustomLyrics(token, true)
            awaitCancellation()
        }
        assertFalse(timedOut)
    }

    @Test fun obsoleteLoadDoesNotStartAnotherRequest() = runTest {
        var requested = false
        runLyricRequest(100, { false }, { error("obsolete timeout") }) { requested = true }
        assertFalse(requested)
    }

    @Test fun completedRequestDoesNotPublishTimeout() = runTest {
        var completed = false
        runLyricRequest(100, { true }, { error("unexpected timeout") }) {
            delay(10)
            completed = true
        }
        assertTrue(completed)
    }

    @Test fun settingReadFailureKeepsDefaultAndSuccessKeepsValue() = runTest {
        assertEquals(8, readLyricSetting(8) { throw IOException("settings unavailable") })
        assertEquals(15, readLyricSetting(8) { 15 })
    }

    @Test fun settingCancellationIsRethrown() = runTest {
        val cancellation = CancellationException("new lyric load")
        val actual = try {
            readLyricSetting(true) { throw cancellation }
            null
        } catch (error: CancellationException) {
            error
        }
        assertSame(cancellation, actual)
    }

    @Test fun cancellingFirstSettingsEmissionCannotFallBackAndContinueLoading() = runTest {
        val settings = MutableSharedFlow<Boolean>()
        var continued = false
        val load = launch {
            readLyricSetting(true) { settings.first() }
            continued = true
        }
        settings.subscriptionCount.first { it == 1 }
        load.cancel()
        load.join()
        assertFalse(continued)
    }

    @Test fun nonCooperativeSettingReadCannotResumeACancelledLoad() = runTest {
        val started = CompletableDeferred<Unit>()
        var continued = false
        val load = launch {
            readLyricSetting(8) {
                started.complete(Unit)
                withContext(NonCancellable) { delay(50) }
                15
            }
            continued = true
        }
        started.await()
        load.cancel()
        load.join()
        assertFalse(continued)
    }
}

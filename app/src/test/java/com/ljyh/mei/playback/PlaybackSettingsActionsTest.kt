package com.ljyh.mei.playback

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.ljyh.mei.constants.LoopPlaybackKey
import com.ljyh.mei.constants.PreviousPlaybackKey
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackSettingsActionsTest {
    @Test fun coldReadSuspendsWithoutBlockingCallerOrUsingDefaults() = runTest {
        val ready = CompletableDeferred<Preferences>()
        val received = mutableListOf<PlaybackControlSettings>()
        val actions = PlaybackSettingsActions(this, flow { emit(ready.await()) }) { throw it }
        actions.submit { received += it }
        runCurrent()
        assertTrue(received.isEmpty())
        var mainThreadWorkRan = false
        launch { mainThreadWorkRan = true }
        runCurrent()
        assertTrue(mainThreadWorkRan)
        ready.complete(preferencesOf(PreviousPlaybackKey to false, LoopPlaybackKey to true))
        runCurrent()
        assertEquals(listOf(PlaybackControlSettings(false, true)), received)
        actions.close()
    }

    @Test fun absentKeysKeepExistingPlaybackDefaults() = runTest {
        val received = mutableListOf<PlaybackControlSettings>()
        val actions = PlaybackSettingsActions(this, MutableStateFlow(emptyPreferences())) { throw it }
        actions.submit { received += it }
        runCurrent()
        assertEquals(listOf(PlaybackControlSettings(true, false)), received)
        actions.close()
    }

    @Test fun everyActionSeesLatestPersistedSettings() = runTest {
        val preferences = MutableStateFlow(emptyPreferences())
        val received = mutableListOf<PlaybackControlSettings>()
        val actions = PlaybackSettingsActions(this, preferences) { throw it }
        actions.submit { received += it }
        runCurrent()
        preferences.value = preferencesOf(PreviousPlaybackKey to false, LoopPlaybackKey to true)
        actions.submit { received += it }
        runCurrent()
        preferences.value = preferencesOf(PreviousPlaybackKey to true, LoopPlaybackKey to false)
        actions.submit { received += it }
        runCurrent()
        assertEquals(listOf(
            PlaybackControlSettings(true, false),
            PlaybackControlSettings(false, true),
            PlaybackControlSettings(true, false),
        ), received)
        actions.close()
    }

    @Test fun rapidPressesRunInOrderAndReadStateAtExecutionTime() = runTest {
        val ready = CompletableDeferred<Preferences>()
        val actions = PlaybackSettingsActions(this, flow { emit(ready.await()) }) { throw it }
        val order = mutableListOf<Int>()
        var mode = 0
        repeat(30) { index ->
            actions.submit {
                order += index
                mode = (mode + 1) % 3
            }
        }
        runCurrent()
        assertTrue(order.isEmpty())
        ready.complete(emptyPreferences())
        runCurrent()
        assertEquals((0 until 30).toList(), order)
        assertEquals(0, mode)
        actions.close()
    }

    @Test fun readFailureReportsErrorWithoutExecutingWithDefaultAndNextPressCanRetry() = runTest {
        val failure = IOException("settings unavailable")
        var reads = 0
        val failures = mutableListOf<Exception>()
        val received = mutableListOf<PlaybackControlSettings>()
        val actions = PlaybackSettingsActions(this, flow {
            if (reads++ == 0) throw failure
            emit(preferencesOf(PreviousPlaybackKey to false))
        }) { failures += it }
        actions.submit { received += it }
        actions.submit { received += it }
        runCurrent()
        assertSame(failure, failures.single())
        assertEquals(listOf(PlaybackControlSettings(false, false)), received)
        actions.close()
    }

    @Test fun disposeRejectsQueuedAndFutureActionsEvenAfterNonCooperativeRead() = runTest {
        val ready = CompletableDeferred<Unit>()
        var executed = 0
        val failures = mutableListOf<Exception>()
        val actions = PlaybackSettingsActions(this, flow {
            withContext(NonCancellable) { ready.await() }
            emit(emptyPreferences())
        }) { failures += it }
        actions.submit { executed++ }
        actions.submit { executed++ }
        runCurrent()
        actions.close()
        actions.submit { executed++ }
        ready.complete(Unit)
        runCurrent()
        assertEquals(0, executed)
        assertTrue(failures.isEmpty())
    }

    @Test fun ownerCancellationDoesNotBecomeSettingsFailure() = runTest {
        val owner = CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
        val ready = CompletableDeferred<Preferences>()
        var executed = false
        val failures = mutableListOf<Exception>()
        val actions = PlaybackSettingsActions(owner, flow { emit(ready.await()) }) { failures += it }
        actions.submit { executed = true }
        runCurrent()
        owner.cancel()
        ready.complete(emptyPreferences())
        runCurrent()
        assertFalse(executed)
        assertTrue(failures.isEmpty())
    }
}

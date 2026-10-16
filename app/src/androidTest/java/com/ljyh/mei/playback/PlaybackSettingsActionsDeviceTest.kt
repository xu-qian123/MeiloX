package com.ljyh.mei.playback

import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ljyh.mei.constants.LoopPlaybackKey
import com.ljyh.mei.constants.PreviousPlaybackKey
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackSettingsActionsDeviceTest {
    @Test fun delayedRealDataStoreKeepsMainLooperResponsiveAndPreservesStoredValues() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "playback-settings-${UUID.randomUUID()}.preferences_pb")
        val storeJob = SupervisorJob()
        val ownerJob = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + storeJob),
            produceFile = { file },
        )
        val gate = CompletableDeferred<Unit>()
        val reading = CompletableDeferred<Unit>()
        val first = CompletableDeferred<PlaybackControlSettings>()
        val second = CompletableDeferred<PlaybackControlSettings>()
        val failed = CompletableDeferred<Exception>()
        val owner = CoroutineScope(Dispatchers.Main + ownerJob)
        var actions: PlaybackSettingsActions? = null
        try {
            // Isolated test file: never read or modify the application's settings store.
            store.edit {
                it[PreviousPlaybackKey] = false
                it[LoopPlaybackKey] = true
            }
            withContext(Dispatchers.Main) {
                actions = PlaybackSettingsActions(owner, flow {
                    reading.complete(Unit)
                    gate.await()
                    emitAll(store.data)
                }) { failed.complete(it) }
                actions!!.submit {
                    assertEquals(Looper.getMainLooper(), Looper.myLooper())
                    first.complete(it)
                }
            }
            withTimeout(5_000) { reading.await() }
            withTimeout(5_000) {
                withContext(Dispatchers.Main) {
                    assertFalse("No temporary defaults while settings are loading", first.isCompleted)
                }
            }
            gate.complete(Unit)
            assertEquals(PlaybackControlSettings(false, true), withTimeout(5_000) { first.await() })
            store.edit {
                it[PreviousPlaybackKey] = true
                it[LoopPlaybackKey] = false
            }
            withContext(Dispatchers.Main) {
                actions!!.submit { second.complete(it) }
            }
            assertEquals(PlaybackControlSettings(true, false), withTimeout(5_000) { second.await() })
            assertFalse(failed.isCompleted)
        } finally {
            withContext(Dispatchers.Main) { actions?.close() }
            ownerJob.cancelAndJoin()
            storeJob.cancelAndJoin()
            withContext(Dispatchers.IO) {
                assertTrue(!file.exists() || file.delete())
            }
        }
    }
}

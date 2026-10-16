package com.ljyh.mei.utils

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ljyh.mei.constants.UserIdKey
import com.ljyh.mei.data.model.room.Playlist
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPlaylistPreferencesDeviceTest {
    @Test fun delayedAccountReadAndPersistedSwitchUpdatePlaylistsWithoutMainThreadWait() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "playlist-preferences-${UUID.randomUUID()}.preferences_pb")
        val storeJob = SupervisorJob()
        val ownerJob = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + storeJob),
            produceFile = { file },
        )
        val gate = CompletableDeferred<Unit>()
        val reading = CompletableDeferred<Unit>()
        val results = Channel<List<String>>(Channel.UNLIMITED)
        val playlists = listOf(
            Playlist("a", "A", "", "A", "", "", 0),
            Playlist("b", "B", "", "B", "", "", 0),
        )
        try {
            store.edit { it[UserIdKey] = "A" }
            CoroutineScope(Dispatchers.Main + ownerJob).launch {
                flow {
                    reading.complete(Unit)
                    gate.await()
                    emitAll(store.data)
                }.playlistsForCurrentUser(flowOf(playlists)).collect { values ->
                    results.send(values.map(Playlist::id))
                }
            }
            withTimeout(5_000) { reading.await() }
            withTimeout(5_000) {
                withContext(Dispatchers.Main) {
                    assertTrue("No list computed from a temporary user ID", results.tryReceive().isFailure)
                }
            }
            gate.complete(Unit)
            assertEquals(listOf("a"), withTimeout(5_000) { results.receive() })
            store.edit { it[UserIdKey] = "B" }
            assertEquals(listOf("b"), withTimeout(5_000) { results.receive() })
            assertEquals("B", withContext(Dispatchers.Main) { store.data.currentUserId() })
            store.edit { it.remove(UserIdKey) }
            assertEquals(emptyList<String>(), withTimeout(5_000) { results.receive() })
        } finally {
            ownerJob.cancelAndJoin()
            storeJob.cancelAndJoin()
            results.cancel()
            withContext(Dispatchers.IO) { assertTrue(!file.exists() || file.delete()) }
        }
    }
}

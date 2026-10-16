package com.ljyh.mei.utils

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.ljyh.mei.constants.UserIdKey
import com.ljyh.mei.data.model.room.Playlist
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UserPlaylistPreferencesTest {
    private fun playlist(id: String, author: String) = Playlist(id, id, "", author, "", "", 0)

    @Test fun constructorFlowDoesNotReadSettingsOrEmitTemporaryIdentity() = runTest {
        val ready = CompletableDeferred<Preferences>()
        var reads = 0
        val result = mutableListOf<List<Playlist>>()
        val filtered = flow { reads++; emit(ready.await()) }
            .playlistsForCurrentUser(flowOf(listOf(playlist("anonymous", ""), playlist("a", "A"))))
        assertEquals(0, reads)
        val job = launch { filtered.collect { result += it } }
        runCurrent()
        assertTrue(result.isEmpty())
        ready.complete(preferencesOf(UserIdKey to "A"))
        runCurrent()
        assertEquals(listOf(listOf("a")), result.map { list -> list.map { it.id } })
        job.cancel()
    }

    @Test fun accountChangesAndPlaylistChangesBothUpdateFiltering() = runTest {
        val settings = MutableStateFlow(preferencesOf(UserIdKey to "A"))
        val playlists = MutableStateFlow(listOf(playlist("a", "A"), playlist("b", "B")))
        val result = mutableListOf<List<String>>()
        val job = launch { settings.playlistsForCurrentUser(playlists).collect { result += it.map(Playlist::id) } }
        runCurrent()
        settings.value = preferencesOf(UserIdKey to "B")
        runCurrent()
        playlists.value += playlist("b2", "B")
        runCurrent()
        settings.value = emptyPreferences()
        runCurrent()
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("b", "b2"), emptyList<String>()), result)
        job.cancel()
    }

    @Test fun eachOperationReadsPersistedAccountInsteadOfConstructorSnapshot() = runTest {
        val settings = MutableStateFlow(preferencesOf(UserIdKey to "A"))
        assertEquals("A", settings.currentUserId())
        settings.value = preferencesOf(UserIdKey to "B")
        assertEquals("B", settings.currentUserId())
        settings.value = emptyPreferences()
        assertEquals("", settings.currentUserId())
    }

    @Test fun cancelledColdReadCannotStartAccountOperation() = runTest {
        val ready = CompletableDeferred<Preferences>()
        var requested = false
        val job = launch {
            flow { emit(ready.await()) }.currentUserId()
            requested = true
        }
        runCurrent()
        job.cancel()
        ready.complete(preferencesOf(UserIdKey to "A"))
        job.join()
        assertFalse(requested)
    }

    @Test fun failedReadIsNotTreatedAsLoggedOutAccount() = runTest {
        val failure = IOException("settings unavailable")
        try {
            flow<Preferences> { throw failure }.currentUserId()
            throw AssertionError("Expected read failure")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
    }
}

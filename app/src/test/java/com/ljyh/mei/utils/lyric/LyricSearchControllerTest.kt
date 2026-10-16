package com.ljyh.mei.utils.lyric

import com.ljyh.mei.data.network.Resource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricSearchControllerTest {
    @Test fun slowOldSearchCannotOverwriteNewSearchEvenIfItIgnoresCancellation() = runTest {
        var displayed: Resource<String> = Resource.Loading
        val search = LyricSearchController<String>(backgroundScope) { displayed = it }
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        search.search({ true }) {
            started.complete(Unit)
            try {
                withContext(NonCancellable) { delay(50) }
                Resource.Success("A")
            } finally {
                finished.complete(Unit)
            }
        }
        started.await()
        search.search({ true }) { Resource.Success("B") }
        finished.await()
        // Yield until the latest search's real publication has run.
        delay(1)
        assertEquals(Resource.Success("B"), displayed)
    }

    @Test fun sameSongReloadRejectsOldSearchResults() = runTest {
        var displayed: Resource<String> = Resource.Loading
        val session = LyricLoadSession()
        val token = session.begin()
        val search = LyricSearchController<String>(backgroundScope) { displayed = it }
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Resource<String>>()
        search.search({ session.isCurrent(token) }) {
            started.complete(Unit)
            result.await()
        }
        started.await()
        session.begin()
        result.complete(Resource.Success("old song"))
        delay(1)
        assertEquals(Resource.Loading, displayed)
    }

    @Test fun automaticSearchCannotReplaceManualSearchResults() = runTest {
        var displayed: Resource<String> = Resource.Loading
        val search = LyricSearchController<String>(backgroundScope) { displayed = it }
        val automaticToken = search.current()
        search.search({ true }) { Resource.Success("manual") }
        delay(1)
        search.publish(automaticToken, Resource.Success("automatic")) { true }
        assertEquals(Resource.Success("manual"), displayed)
    }

    @Test fun cancellingAllSearchesInvalidatesLatePublications() = runTest {
        var displayed: Resource<String> = Resource.Loading
        val search = LyricSearchController<String>(backgroundScope) { displayed = it }
        val token = search.current()
        search.cancel()
        search.publish(token, Resource.Success("late")) { true }
        assertEquals(Resource.Loading, displayed)
    }
}

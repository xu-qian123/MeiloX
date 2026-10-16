package com.ljyh.mei.data.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class ResponseHandlerTest {
    @Test fun successKeepsPayload() = runTest {
        assertEquals(Resource.Success("lyrics"), safeApiCall { "lyrics" })
    }

    @Test fun networkFailureKeepsExistingErrorContract() = runTest {
        assertEquals(Resource.Error("offline"), safeApiCall<String> { throw IOException("offline") })
        assertEquals(Resource.Error("Unknown Error"), safeApiCall<String> { throw IOException() })
    }

    @Test fun cancellationIsRethrownWithoutConvertingToError() = runTest {
        val cancellation = CancellationException("track changed")
        val actual = try {
            safeApiCall<String> { throw cancellation }
            null
        } catch (error: CancellationException) {
            error
        }
        assertSame(cancellation, actual)
    }

    @Test fun cancellingSuspendedRequestDoesNotRunErrorConsumer() = runTest {
        val started = CompletableDeferred<Unit>()
        var consumed = false
        val request = launch {
            safeApiCall {
                started.complete(Unit)
                awaitCancellation()
            }
            consumed = true
        }
        started.await()
        request.cancel()
        request.join()
        assertFalse(consumed)
    }

    @Test fun lateResponseOrNetworkFailureCannotResumeCancelledConsumer() = runTest {
        for (fails in listOf(false, true)) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var consumed = false
            val request = launch {
                safeApiCall {
                    started.complete(Unit)
                    withContext(NonCancellable) { release.await() }
                    if (fails) throw IOException("late failure")
                    "late lyrics"
                }
                consumed = true
            }
            started.await()
            request.cancel()
            release.complete(Unit)
            request.join()
            assertFalse(consumed)
        }
    }
}

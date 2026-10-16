package com.ljyh.mei.utils

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.ljyh.mei.constants.DownloadQuality
import com.ljyh.mei.constants.DownloadQualityKey
import com.ljyh.mei.constants.MusicQuality
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadPreferencesTest {
    @Test fun requestedQualityDoesNotReadPreferences() = runTest {
        assertEquals(MusicQuality.LOSSLESS, flow<Preferences> {
            throw AssertionError("Explicit quality should bypass saved quality")
        }.downloadQuality(MusicQuality.LOSSLESS))
    }

    @Test fun allExistingSavedQualityValuesKeepTheirMapping() = runTest {
        DownloadQuality.entries.forEach { quality ->
            assertEquals(quality.toMusicQuality(), flowOf(preferencesOf(DownloadQualityKey to quality.name)).downloadQuality(null))
        }
    }

    @Test fun absentAndInvalidValuesKeepExhighFallback() = runTest {
        assertEquals(MusicQuality.EXHIGH, flowOf(emptyPreferences()).downloadQuality(null))
        assertEquals(MusicQuality.EXHIGH, flowOf(preferencesOf(DownloadQualityKey to "obsolete-quality")).downloadQuality(null))
    }

    @Test fun ordinaryReadFailureKeepsExistingFallback() = runTest {
        assertEquals(MusicQuality.EXHIGH, flow<Preferences> { throw IOException("unavailable") }.downloadQuality(null))
    }

    @Test fun cancellationWhileReadingDoesNotContinueWithFallbackQuality() = runTest {
        val ready = CompletableDeferred<Preferences>()
        var enqueued = false
        val job = launch {
            flow { emit(ready.await()) }.downloadQuality(null)
            enqueued = true
        }
        runCurrent()
        job.cancel()
        ready.complete(emptyPreferences())
        job.join()
        assertFalse(enqueued)
    }
}

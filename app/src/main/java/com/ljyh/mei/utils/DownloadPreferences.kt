package com.ljyh.mei.utils

import androidx.datastore.preferences.core.Preferences
import com.ljyh.mei.constants.DownloadQuality
import com.ljyh.mei.constants.DownloadQualityKey
import com.ljyh.mei.constants.MusicQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal suspend fun Flow<Preferences>.downloadQuality(requested: MusicQuality?): MusicQuality {
    currentCoroutineContext().ensureActive()
    if (requested != null) return requested
    return try {
        val saved = first()[DownloadQualityKey]
        currentCoroutineContext().ensureActive()
        saved?.let { DownloadQuality.valueOf(it).toMusicQuality() } ?: MusicQuality.EXHIGH
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        MusicQuality.EXHIGH
    }
}

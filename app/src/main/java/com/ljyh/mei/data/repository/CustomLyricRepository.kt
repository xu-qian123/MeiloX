package com.ljyh.mei.data.repository

import com.ljyh.mei.data.model.room.CustomLyric
import com.ljyh.mei.di.dao.CustomLyricDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Atomic persistence for edited lyrics, offsets and legacy song-key migration. */
class CustomLyricRepository(private val dao: CustomLyricDao) {
    private val mutex = Mutex()

    // Never turn a failed read into "no saved lyric": that could overwrite existing text.
    private suspend fun update(
        stableKey: String,
        transform: (CustomLyric?) -> CustomLyric?,
    ): CustomLyric? = mutex.withLock {
        require(stableKey.isNotBlank()) { "Missing song identity" }
        dao.updateSong(stableKey, transform)
    }

    suspend fun get(stableKey: String): CustomLyric? = update(stableKey) { it }

    suspend fun saveLyric(
        stableKey: String,
        lyric: String?,
        translatedLyric: String?,
        matchedSource: String? = null,
        matchedSongId: String? = null,
    ) {
        update(stableKey) { current ->
            (current ?: CustomLyric(stableKey)).copy(
                lyric = lyric?.takeIf { it.isNotBlank() },
                translatedLyric = translatedLyric?.takeIf { it.isNotBlank() },
                matchedLyricSource = matchedSource ?: current?.matchedLyricSource,
                matchedSongId = matchedSongId ?: current?.matchedSongId,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun saveOffset(stableKey: String, offsetMs: Long) {
        update(stableKey) { current ->
            (current ?: CustomLyric(stableKey)).copy(
                userLyricOffsetMs = offsetMs.takeIf { it != 0L },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun clearLyric(stableKey: String) {
        update(stableKey) { current ->
            current?.copy(
                lyric = null,
                translatedLyric = null,
                matchedLyricSource = null,
                matchedSongId = null,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun clearOffset(stableKey: String) {
        update(stableKey) { it?.copy(userLyricOffsetMs = null, updatedAt = System.currentTimeMillis()) }
    }
}

internal fun selectLatestCustomLyric(rows: List<CustomLyric>): CustomLyric? =
    rows.maxByOrNull { it.updatedAt }
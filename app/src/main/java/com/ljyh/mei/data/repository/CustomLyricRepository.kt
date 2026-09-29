package com.ljyh.mei.data.repository

import com.ljyh.mei.data.model.room.CustomLyric
import com.ljyh.mei.di.dao.CustomLyricDao
import timber.log.Timber

/**
 * Persistence for user-edited lyrics and per-song lyric preferences.
 *
 * Ported from NeriPlayer's `CustomSongLyricsRepository` but backed by Room
 * instead of a JSON file. Field-level updates keep lyric text, translation,
 * offset and match metadata independent from each other.
 */
class CustomLyricRepository(
    private val dao: CustomLyricDao,
) {
    private val tag = "CustomLyricRepository"

    suspend fun get(stableKey: String): CustomLyric? {
        if (stableKey.isBlank()) return null
        return runCatching { dao.get(stableKey) }
            .onFailure { Timber.tag(tag).w(it, "Failed to read custom lyric for $stableKey") }
            .getOrNull()
    }

    suspend fun saveLyric(
        stableKey: String,
        lyric: String?,
        translatedLyric: String?,
        matchedSource: String? = null,
        matchedSongId: String? = null,
    ) {
        if (stableKey.isBlank()) return
        val current = get(stableKey)
        val updated = (current ?: CustomLyric(stableKey = stableKey)).copy(
            lyric = lyric?.takeIf { it.isNotBlank() },
            translatedLyric = translatedLyric?.takeIf { it.isNotBlank() },
            matchedLyricSource = matchedSource ?: current?.matchedLyricSource,
            matchedSongId = matchedSongId ?: current?.matchedSongId,
            updatedAt = System.currentTimeMillis(),
        )
        upsert(updated)
        Timber.tag(tag).d(
            "Saved custom lyric for $stableKey (lyricLen=${lyric?.length ?: 0}, transLen=${translatedLyric?.length ?: 0})",
        )
    }

    suspend fun saveOffset(stableKey: String, offsetMs: Long) {
        if (stableKey.isBlank()) return
        val current = get(stableKey)
        val updated = (current ?: CustomLyric(stableKey = stableKey)).copy(
            userLyricOffsetMs = offsetMs.takeIf { it != 0L },
            updatedAt = System.currentTimeMillis(),
        )
        upsert(updated)
    }

    suspend fun clearLyric(stableKey: String) {
        if (stableKey.isBlank()) return
        val current = get(stableKey) ?: return
        val updated = current.copy(
            lyric = null,
            translatedLyric = null,
            matchedLyricSource = null,
            matchedSongId = null,
            updatedAt = System.currentTimeMillis(),
        )
        upsert(updated)
        Timber.tag(tag).d("Cleared custom lyric for $stableKey")
    }

    suspend fun clearOffset(stableKey: String) {
        if (stableKey.isBlank()) return
        val current = get(stableKey) ?: return
        upsert(current.copy(userLyricOffsetMs = null, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun upsert(entry: CustomLyric) {
        if (entry.isEmpty()) {
            dao.delete(entry.stableKey)
        } else {
            dao.upsert(entry)
        }
    }
}

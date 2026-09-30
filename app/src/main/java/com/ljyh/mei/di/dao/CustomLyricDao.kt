package com.ljyh.mei.di.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ljyh.mei.data.model.room.CustomLyric
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomLyricDao {
    /** Migration and field updates share one transaction; failures never delete the old row. */
    @Transaction
    suspend fun updateSong(
        stableKey: String,
        update: (CustomLyric?) -> CustomLyric?,
    ): CustomLyric? {
        val rows = getAllForSongKey(stableKey)
        val current = rows.maxByOrNull { it.updatedAt }?.copy(stableKey = stableKey)
        val updated = update(current)?.takeUnless { it.isEmpty() }?.copy(stableKey = stableKey)
        if (updated == null) {
            delete(stableKey)
        } else if (rows.size != 1 || rows.firstOrNull() != updated) {
            upsert(updated)
        }
        deleteLegacyForSongKey(stableKey)
        return updated
    }

    @Query("SELECT * FROM custom_lyric WHERE stableKey = :stableKey LIMIT 1")
    suspend fun get(stableKey: String): CustomLyric?

    @Query("SELECT * FROM custom_lyric WHERE stableKey = :stableKey LIMIT 1")
    fun observe(stableKey: String): Flow<CustomLyric?>

    /**
     * 同一首歌的所有记录：新键（无 `|`）与旧键（`歌曲身份|专辑`）。
     * `substr(...)` 在无 `|` 时返回空串，因此不会误匹配。
     */
    @Query(
        "SELECT * FROM custom_lyric WHERE stableKey = :stableKey " +
            "OR substr(stableKey, 1, instr(stableKey, '|') - 1) = :stableKey " +
            "ORDER BY updatedAt DESC"
    )
    suspend fun getAllForSongKey(stableKey: String): List<CustomLyric>

    /** 删除同一首歌残留的旧键记录，保留 [stableKey] 对应的新键记录。 */
    @Query(
        "DELETE FROM custom_lyric WHERE stableKey != :stableKey " +
            "AND substr(stableKey, 1, instr(stableKey, '|') - 1) = :stableKey"
    )
    suspend fun deleteLegacyForSongKey(stableKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CustomLyric)

    @Query("DELETE FROM custom_lyric WHERE stableKey = :stableKey")
    suspend fun delete(stableKey: String)
}

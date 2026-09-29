package com.ljyh.mei.di.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ljyh.mei.data.model.room.CustomLyric
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomLyricDao {
    @Query("SELECT * FROM custom_lyric WHERE stableKey = :stableKey LIMIT 1")
    suspend fun get(stableKey: String): CustomLyric?

    @Query("SELECT * FROM custom_lyric WHERE stableKey = :stableKey LIMIT 1")
    fun observe(stableKey: String): Flow<CustomLyric?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CustomLyric)

    @Query("DELETE FROM custom_lyric WHERE stableKey = :stableKey")
    suspend fun delete(stableKey: String)
}

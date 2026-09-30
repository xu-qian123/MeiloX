package com.ljyh.mei.di.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ljyh.mei.data.model.room.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist where id=:id")
    suspend fun getPlaylist(id: String): Playlist?

    @Query("SELECT * FROM playlist where author=:author")
    suspend fun getPlaylistByAuthor(author: String): List<Playlist>

    // 明确排序：网易云 API 返回的下标（与 App 内一致），本地歌单为 0 排最前。
    @Query("SELECT * FROM playlist ORDER BY sortOrder ASC, id ASC")
    fun getAllPlaylist(): Flow<List<Playlist>>

    // Upsert 保留已有行的 rowid，不再像 REPLACE 那样每次同步都重写。
    @Upsert
    suspend fun insertPlaylist(playlist: Playlist)

    @Upsert
    suspend fun insertPlaylists(playlists: List<Playlist>)

    @Query("DELETE FROM playlist where id=:id")
    suspend fun deletePlaylistById(id: String)

    @Query("UPDATE playlist SET lastPlayTime = :timestamp, localPlayCount = localPlayCount + 1 WHERE id = :id")
    suspend fun touchPlaylist(id: String, timestamp: Long)
}

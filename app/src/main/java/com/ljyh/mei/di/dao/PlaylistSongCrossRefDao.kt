package com.ljyh.mei.di.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ljyh.mei.data.model.room.PlaylistSongCrossRef
import com.ljyh.mei.data.model.room.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistSongCrossRefDao {
    @Query("SELECT songId FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun getSongIdsByPlaylist(playlistId: String): Flow<List<String>>

    /** 按歌单顺序取出完整歌曲行（喜欢列表缓存走这里）。 */
    @Query(
        """
        SELECT song.* FROM song
        INNER JOIN playlist_song_cross_ref AS ref ON song.id = ref.songId
        WHERE ref.playlistId = :playlistId
        ORDER BY ref.sortOrder
        """
    )
    fun getSongsByPlaylist(playlistId: String): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<PlaylistSongCrossRef>)

    /** 整体替换某个歌单的歌曲顺序（删除与写入放在同一事务，避免中间态闪空）。 */
    @Transaction
    suspend fun replacePlaylistSongs(playlistId: String, songIds: List<String>) {
        deleteByPlaylist(playlistId)
        if (songIds.isNotEmpty()) {
            insertAll(songIds.mapIndexed { index, songId ->
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, sortOrder = index)
            })
        }
    }

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun delete(playlistId: String, songId: String)
}

package com.ljyh.mei.di.repository

import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.data.model.room.Song
import com.ljyh.mei.di.dao.PlaylistSongCrossRefDao
import com.ljyh.mei.di.dao.SongDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「我喜欢的音乐」本地缓存。
 *
 * 网易云的喜欢列表只有网络来源，之前每次进入媒体库都会全量拉一次。这里把歌曲元数据
 * 落进 `song` 表、顺序落进 `playlist_song_cross_ref` 的系统槽位，进入时先读缓存
 * （秒开、离线可用），下拉刷新或首次进入再同步。
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val songDao: SongDao,
    private val crossRefDao: PlaylistSongCrossRefDao,
) {
    fun getFavoriteSongs(): Flow<List<Song>> = crossRefDao.getSongsByPlaylist(FAVORITES_PLAYLIST_ID)

    suspend fun replaceFavoriteSongs(tracks: List<MediaMetadata>) {
        // 空结果（网络异常等）不覆盖已有缓存。
        if (tracks.isEmpty()) return
        // 只补缺失的歌曲行，避免把已有的本地/已下载元数据冲掉。
        songDao.insertSongsIfAbsent(tracks.map { it.toCachedSong() })
        crossRefDao.replacePlaylistSongs(FAVORITES_PLAYLIST_ID, tracks.map { it.id.toString() })
    }

    companion object {
        /** 系统槽位：不是真实歌单行，只用于给交叉引用表分组。 */
        const val FAVORITES_PLAYLIST_ID = "library_favorites_cache"
    }
}

private fun MediaMetadata.toCachedSong(): Song = Song(
    id = id.toString(),
    title = title,
    artist = artists.map { it.name },
    album = album.title,
    cover = coverUrl,
    duration = duration,
)

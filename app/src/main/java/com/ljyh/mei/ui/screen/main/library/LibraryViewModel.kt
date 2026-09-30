package com.ljyh.mei.ui.screen.main.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.data.model.AlbumPhoto
import com.ljyh.mei.data.model.UserAccount
import com.ljyh.mei.data.model.UserAlbumList
import com.ljyh.mei.data.model.UserPlaylist
import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.data.model.toMediaMetadata
import com.ljyh.mei.data.model.toMiniPlaylistDetail
import com.ljyh.mei.data.model.room.AlbumEntity
import com.ljyh.mei.data.model.room.ArtistEntity
import com.ljyh.mei.data.model.room.Playlist
import com.ljyh.mei.data.model.weapi.UserSubcount
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.UserRepository
import com.ljyh.mei.data.repository.PlaylistRepository
import com.ljyh.mei.di.repository.AlbumsRepository
import com.ljyh.mei.di.repository.FavoritesRepository
import com.ljyh.mei.di.repository.LocalPlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: UserRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val albumsRepository: AlbumsRepository,
    private val playlistRepository: PlaylistRepository,
    private val favoritesRepository: FavoritesRepository,
):ViewModel() {
    private val _account = MutableStateFlow<Resource<UserAccount>>(Resource.Loading)
    val account: StateFlow<Resource<UserAccount>> = _account

    private val _photoAlbum=MutableStateFlow<Resource<AlbumPhoto>>(Resource.Loading)
    val photoAlbum:StateFlow<Resource<AlbumPhoto>> = _photoAlbum

    private val _networkPlaylistsState = MutableStateFlow<Resource<UserPlaylist>>(Resource.Loading)
    val networkPlaylistsState: StateFlow<Resource<UserPlaylist>> = _networkPlaylistsState

    private val _albumList = MutableStateFlow<Resource<UserAlbumList>>(Resource.Loading)
    val albumList: StateFlow<Resource<UserAlbumList>> = _albumList

    private val _userSubcount = MutableStateFlow<Resource<UserSubcount>>(Resource.Loading)
    val userSubcount: StateFlow<Resource<UserSubcount>> = _userSubcount

    /** 「我喜欢的音乐」的本地缓存（Room），进入时先读它；下拉刷新才走网络。 */
    val favoriteSongs: StateFlow<List<MediaMetadata>> = favoritesRepository.getFavoriteSongs()
        .map { songs -> songs.map { it.toMediaMetadata() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList(),
        )

    private val _favoriteSongsRefreshing = MutableStateFlow(false)
    val favoriteSongsRefreshing: StateFlow<Boolean> = _favoriteSongsRefreshing

    private val _playlistsRefreshing = MutableStateFlow(false)
    val playlistsRefreshing: StateFlow<Boolean> = _playlistsRefreshing

    private var likedPlaylistId: Long? = null

    val localPlaylists: StateFlow<List<Playlist>> = localPlaylistRepository.getAllPlaylist()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L), // 5秒内无订阅者则停止
            initialValue = emptyList() // 初始值为空列表
        )

    /**
     * 媒体库的自动同步每个进程只做一次；之后进入直接用本地缓存，手动下拉刷新即可。
     */
    fun consumeAutoSync(): Boolean {
        if (autoSyncedThisProcess) return false
        autoSyncedThisProcess = true
        return true
    }

    fun getUserAccount() {
        if (account.value is Resource.Success) return
        viewModelScope.launch {
            _account.value = Resource.Loading
            _account.value = repository.getUserAccount()
        }
    }

    fun getPhotoAlbum(id:String){
        viewModelScope.launch {
            _photoAlbum.value = Resource.Loading
            _photoAlbum.value = repository.getPhotoAlbum(id)
        }
    }

    fun syncUserPlaylists(uid: String, limit: Int = 100, refreshing: Boolean = false) {
        if (_playlistsRefreshing.value) return
        viewModelScope.launch {
            if (refreshing) _playlistsRefreshing.value = true
            try {
                // 下拉刷新时保持旧的 Success，避免 UI 的 likedPlaylistId 瞬间置空
                // 导致「我喜欢的音乐」闪现回列表、看起来排序错乱。
                if (_networkPlaylistsState.value !is Resource.Success) {
                    _networkPlaylistsState.value = Resource.Loading
                }
                when (val networkResult = repository.getUserPlaylist(uid, limit)) {
                    is Resource.Success -> {
                        val existingPlaylists = localPlaylistRepository.getPlaylistByAuthor(uid)
                        val existingMap = existingPlaylists.associateBy { it.id }
                        // 保留网易云返回的顺序：下标写进 sortOrder，UI 按它展示。
                        val playlistsToInsert = networkResult.data.playlist.mapIndexed { index, item ->
                            val existing = existingMap[item.id.toString()]
                            Playlist(
                                id = item.id.toString(),
                                title = item.name,
                                cover = item.coverImgUrl,
                                author = item.creator.userId.toString(),
                                authorName = item.creator.nickname,
                                authorAvatar = item.creator.avatarUrl,
                                count = item.trackCount,
                                playCount = item.playCount,
                                lastPlayTime = existing?.lastPlayTime ?: 0L,
                                localPlayCount = existing?.localPlayCount ?: 0,
                                sortOrder = index,
                            )
                        }
                        localPlaylistRepository.insertPlaylists(playlistsToInsert)
                        _networkPlaylistsState.value = networkResult
                        likedPlaylistId = networkResult.data.playlist.firstOrNull()?.id
                        refreshLikedSongs(uid)
                    }
                    is Resource.Error -> {
                        // 手动下拉失败时保留旧列表，避免 likedPlaylistId 闪断导致错乱。
                        if (!refreshing) {
                            _networkPlaylistsState.value = networkResult
                        }
                    }
                    Resource.Loading -> { }
                }
            } finally {
                if (refreshing) _playlistsRefreshing.value = false
            }
        }
    }

    /** 拉取「我喜欢的音乐」并写入本地缓存；进入时只读缓存，不会自动调用。 */
    fun refreshLikedSongs(uid: String) {
        if (_favoriteSongsRefreshing.value) return
        viewModelScope.launch {
            _favoriteSongsRefreshing.value = true
            try {
                val playlistId = likedPlaylistId ?: resolveLikedPlaylistId(uid) ?: return@launch
                when (val result = playlistRepository.getPlaylistDetail(playlistId.toString())) {
                    is Resource.Success -> {
                        val tracks = try {
                            playlistRepository.getCompletePlaylistTracks(result.data)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            result.data.toMiniPlaylistDetail().tracks
                        }
                        favoritesRepository.replaceFavoriteSongs(tracks)
                    }
                    else -> Unit
                }
            } finally {
                _favoriteSongsRefreshing.value = false
            }
        }
    }

    /** 尚未同步过歌单列表时，先取一次以便拿到「我喜欢的音乐」的歌单 id。 */
    private suspend fun resolveLikedPlaylistId(uid: String): Long? {
        val result = repository.getUserPlaylist(uid, 100)
        if (result is Resource.Success) {
            _networkPlaylistsState.value = result
            likedPlaylistId = result.data.playlist.firstOrNull()?.id
        }
        return likedPlaylistId
    }

    fun getAlbumList(){
        viewModelScope.launch {
            _albumList.value= Resource.Loading
            _albumList.value=repository.getAlbumList()
        }
    }

    fun getUserSubcount(){
        viewModelScope.launch {
            _userSubcount.value= Resource.Loading
            _userSubcount.value=repository.getUsrSubcount()
        }
    }

    fun insertAlbum(album: AlbumEntity, artists: List<ArtistEntity>){
        viewModelScope.launch {
            albumsRepository.insertAlbum(album, artists)
        }
    }

    private companion object {
        @Volatile
        var autoSyncedThisProcess = false
    }
}

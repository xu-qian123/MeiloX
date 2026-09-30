package com.ljyh.mei.ui.screen.main.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ljyh.mei.ui.navigation.MeiNavigator
import com.ljyh.mei.R
import androidx.compose.ui.graphics.Color
import com.ljyh.mei.constants.LibraryStyle
import com.ljyh.mei.constants.LibraryStyleKey
import com.ljyh.mei.utils.rememberEnumPreference
import com.ljyh.mei.ui.glass.IosListRow
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.constants.CookieKey
import com.ljyh.mei.constants.UserAvatarUrlKey
import com.ljyh.mei.constants.UserIdKey
import com.ljyh.mei.constants.UserNicknameKey
import com.ljyh.mei.constants.UserPhotoKey
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.model.toAlbum
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.component.GlobalProfileAvatarButton
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.screen.main.library.component.LibraryMobileLayout
import com.ljyh.mei.ui.screen.main.library.component.PhotoPickerSheet
import com.ljyh.mei.ui.navigation.LibraryPage
import com.ljyh.mei.utils.rememberPreference

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    isNavigationTab: Boolean = false,
    category: LibraryPage? = null,
) {
    val libraryStyle by rememberEnumPreference(LibraryStyleKey, LibraryStyle.Default)
    if (category == null && libraryStyle == LibraryStyle.AppleMusic) {
        LibraryCategoryList(isNavigationTab)
        return
    }
    val navController = LocalNavController.current
    val account by viewModel.account.collectAsState()
    val photoAlbum by viewModel.photoAlbum.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()
    val albumList by viewModel.albumList.collectAsState()
    val userSubcount by viewModel.userSubcount.collectAsState()
    val networkPlaylists by viewModel.networkPlaylistsState.collectAsState()
    val likedSongs by viewModel.favoriteSongs.collectAsState()
    val likedSongsRefreshing by viewModel.favoriteSongsRefreshing.collectAsState()
    val playlistsRefreshing by viewModel.playlistsRefreshing.collectAsState()

    // Preferences
    val (userId, setUserId) = rememberPreference(UserIdKey, "")
    val (_, setUserNickname) = rememberPreference(UserNicknameKey, "")
    val (_, setUserAvatarUrl) = rememberPreference(UserAvatarUrlKey, "")
    val (userPhoto, setUserPhoto) = rememberPreference(UserPhotoKey, "")
    val cookie by rememberPreference(CookieKey, defaultValue = "")

    // State
    var showPhotoPicker by remember { mutableStateOf(false) }
    var selectedPage by rememberSaveable { mutableStateOf(LibraryPage.Songs) }

    val freshLikedPlaylistId = (networkPlaylists as? Resource.Success)?.data?.playlist?.firstOrNull()?.id
    // 刷新期间 networkPlaylists 可能短暂非 Success，用上一次非空值兜底，
    // 防止「我喜欢的音乐」闪现回列表造成排序跳动。
    var stableLikedPlaylistId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(freshLikedPlaylistId) {
        if (freshLikedPlaylistId != null) stableLikedPlaylistId = freshLikedPlaylistId
    }
    val likedPlaylistId = freshLikedPlaylistId ?: stableLikedPlaylistId
    val visiblePlaylists = remember(localPlaylists, likedPlaylistId) {
        localPlaylists.filterNot { it.id == likedPlaylistId?.toString() }
    }
    val (createdPlaylists, collectedPlaylists) = remember(visiblePlaylists, userId) {
        if (userId.isEmpty()) Pair(emptyList(), emptyList())
        else {
            val (created, collected) = visiblePlaylists.partition { it.author == userId }
            // 顺序与网易云 App 一致：按 API 下标（本地歌单为 0，排各组最前）。
            Pair(
                created.sortedBy { it.sortOrder },
                collected.sortedBy { it.sortOrder },
            )
        }
    }

    // --- 数据同步逻辑 ---
    // 首次进入（每个进程一次）自动同步；之后进入直接用本地缓存，手动下拉刷新即可。
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            if (viewModel.consumeAutoSync()) {
                viewModel.syncUserPlaylists(userId)
            }
            viewModel.getPhotoAlbum(userId)
            viewModel.getAlbumList()
            viewModel.getUserSubcount()
        }
    }

    LaunchedEffect(photoAlbum) {
        if (userPhoto.isEmpty() && photoAlbum is Resource.Success) {
            (photoAlbum as Resource.Success).data.data.records.firstOrNull()?.imageUrl?.let {
                setUserPhoto(it)
            }
        }
    }
    LaunchedEffect(cookie, account) {
        if (cookie.isNotEmpty() && account !is Resource.Success) viewModel.getUserAccount()
    }
    LaunchedEffect(account) {
        (account as? Resource.Success)
            ?.data?.profile
            ?.let { profile ->
                setUserId(profile.userId.toString())
                setUserNickname(profile.nickname)
                setUserAvatarUrl(profile.avatarUrl)
            }
    }

    LaunchedEffect(userSubcount) {
        if (userSubcount is Resource.Success) {

        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (userId.isNotEmpty()) {
            LibraryMobileLayout(
                userPhoto = userPhoto,
                isNavigationTab = isNavigationTab,
                selectedPage = category ?: selectedPage,
                isCategoryPage = category != null,
                onPageSelect = { selectedPage = it },
                createdPlaylists = createdPlaylists,
                collectedPlaylists = collectedPlaylists,
                albums = if (albumList is Resource.Success) (albumList as Resource.Success).data.data.map { it.toAlbum() } else emptyList(),
                onPlaylistClick = { id->
                    Screen.PlayList.navigate(navController) { addPath(id) }
                },
                onAlbumClick = { id->
                    Screen.Album.navigate(navController) { addPath(id) }
                },
                userId = userId,
                likedSongs = likedSongs,
                // 只有「正在刷新且本地为空」时才显示加载圈，其余情况直接展示缓存。
                likedSongsLoading = likedSongsRefreshing && likedSongs.isEmpty(),
                likedSongsRefreshing = likedSongsRefreshing,
                playlistsRefreshing = playlistsRefreshing,
                onRefreshLikedSongs = { if (userId.isNotEmpty()) viewModel.refreshLikedSongs(userId) },
                onRefreshPlaylists = { if (userId.isNotEmpty()) viewModel.syncUserPlaylists(userId, refreshing = true) },
            )

            if (showPhotoPicker) {
                PhotoPickerSheet(
                    photoAlbum = photoAlbum,
                    onSelect = { setUserPhoto(it); showPhotoPicker = false },
                    onDismiss = { showPhotoPicker = false }
                )
            }
        } else {
            // 未登录逻辑
            EmptyLoginState(navController, isNavigationTab, category)
        }
    }
}

@Composable
fun EmptyLoginState(
    navController: MeiNavigator,
    isNavigationTab: Boolean = false,
    category: LibraryPage? = null,
) {
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    IosPinnedListPage(
        title = stringResource(category?.titleRes ?: R.string.app_tab_library),
        bottomPadding = insets.calculateBottomPadding(),
        onNavigateBack = if (isNavigationTab) null else ({ navController.navigateUp() }),
        actions = {
            if (isNavigationTab) GlobalProfileAvatarButton()
        },
    ) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                com.ljyh.mei.ui.glass.GlassCard(
                    modifier = Modifier.padding(24.dp),
                    onClick = { Screen.NeteaseLogin.navigate(navController) },
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        com.ljyh.mei.ui.glass.SfIcon("person.crop.circle", null, size = 42.dp)
                        Text(
                            stringResource(com.ljyh.mei.R.string.library_sign_in),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCategoryList(isNavigationTab: Boolean) {
    val navController = LocalNavController.current
    val colors = LocalGlassColors.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    IosPinnedListPage(
        title = stringResource(R.string.app_tab_library),
        bottomPadding = insets.calculateBottomPadding(),
        horizontalContentPadding = 6.dp,
        largeTitleHorizontalPadding = 14.dp,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        backgroundColor = if (colors.isDark) colors.groupedBackground else Color.White,
        onNavigateBack = if (isNavigationTab) null else ({ navController.navigateUp() }),
        actions = { if (isNavigationTab) GlobalProfileAvatarButton() },
    ) {
        LibraryPage.entries.forEachIndexed { index, page ->
            item(key = "library-category:${page.name}") {
                IosListRow(
                    title = stringResource(page.titleRes),
                    leading = { SfIcon(page.symbol, null, tint = colors.accent) },
                    showTopSeparator = index > 0,
                    onClick = {
                        Screen.LibraryCategory.navigate(navController) { addPath(page.name) }
                    },
                )
            }
        }
    }
}

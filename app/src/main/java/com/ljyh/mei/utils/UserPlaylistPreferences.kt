package com.ljyh.mei.utils

import androidx.datastore.preferences.core.Preferences
import com.ljyh.mei.constants.UserIdKey
import com.ljyh.mei.data.model.room.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal suspend fun Flow<Preferences>.currentUserId(): String = first()[UserIdKey] ?: ""

internal fun Flow<Preferences>.playlistsForCurrentUser(
    playlists: Flow<List<Playlist>>,
): Flow<List<Playlist>> = map { it[UserIdKey] ?: "" }
    .distinctUntilChanged()
    .combine(playlists) { userId, values -> values.filter { it.author == userId } }

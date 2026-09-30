package com.ljyh.mei.data.model.room

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "playlist")
data class Playlist(
    @PrimaryKey val id: String,
    val title: String,
    val cover: String,
    val author: String,
    val authorName: String,
    val authorAvatar: String,
    val count: Int,
    val type: PlaylistType = PlaylistType.NETEAST,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val playCount: Long = 0L,
    val lastPlayTime: Long = 0L,
    val localPlayCount: Int = 0,
    /**
     * 网易云 `user/playlist` 返回列表里的下标，用于保持与网易云 App 一致的顺序。
     * 本地创建的歌单没有下标，保持 0（排在各组最前）。
     */
    val sortOrder: Int = 0,
)

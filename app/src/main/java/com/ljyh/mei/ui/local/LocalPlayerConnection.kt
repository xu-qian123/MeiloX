package com.ljyh.mei.ui.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.ljyh.mei.playback.PlayerConnection

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }

/**
 * 当前媒体项 id（对应列表项的 `MediaMetadata.id`）。
 * 用于列表的「正在播放」高亮：暂停时仍然返回当前歌曲，不做播放状态判定。
 */
@Composable
fun rememberCurrentSongId(): Long? {
    val playerConnection = LocalPlayerConnection.current ?: return null
    val metadata by playerConnection.mediaMetadata.collectAsState()
    return metadata?.id
}
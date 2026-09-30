package com.ljyh.mei.data.model.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-edited lyrics and per-song lyric preferences.
 *
 * Rows are keyed by [stableKey] (see `MediaMetadata.stableKey()`), which only uses the
 * song identity and stays consistent across app restarts and entry points.
 * 历史数据可能带 `歌曲身份|专辑` 旧主键，由 `CustomLyricRepository` 读取时迁移。
 */
@Entity(tableName = "custom_lyric")
data class CustomLyric(
    @PrimaryKey val stableKey: String,
    val lyric: String? = null,
    val translatedLyric: String? = null,
    /** 0 ms is stored as null. */
    val userLyricOffsetMs: Long? = null,
    /** KUGOU / CLOUD_MUSIC / QQ_MUSIC / AMLL_TTML, filled by the match feature. */
    val matchedLyricSource: String? = null,
    val matchedSongId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun isEmpty(): Boolean =
        lyric.isNullOrBlank() &&
            translatedLyric.isNullOrBlank() &&
            userLyricOffsetMs == null &&
            matchedLyricSource.isNullOrBlank() &&
            matchedSongId.isNullOrBlank()
}

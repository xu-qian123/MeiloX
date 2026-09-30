package com.ljyh.mei.data.model.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_lyric")
data class CachedLyric(
    @PrimaryKey val songId: String,
    val content: String,
    val translation: String?,
    /** 音译文本（网易云 romalrc / QQ roma），可为空。 */
    val romanization: String? = null,
    val isVerbatim: Boolean,
    val isPureMusic: Boolean,
    val sourceName: String,
    val parserType: String,
    val updatedAt: Long
)

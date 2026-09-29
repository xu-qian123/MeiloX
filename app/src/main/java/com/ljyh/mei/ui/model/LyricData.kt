package com.ljyh.mei.ui.model

import com.ljyh.mei.data.model.Lyric
import com.ljyh.mei.data.model.qq.u.LyricResult
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics


data class LyricData(
    val isVerbatim: Boolean = false,
    val isPureMusic: Boolean = false,
    val source: LyricSource = LyricSource.Empty,
    val lyricLine: SyncedLyrics,
    /** Raw text of the lyric source, when known. Used by the lyrics editor. */
    val rawLyrics: String? = null,
    /** Raw translation text of the lyric source, when known. */
    val rawTranslation: String? = null,
)


sealed class LyricSourceData(val source: LyricSource, val priority: Int) {
    data class NetEase(val lyric: Lyric) : LyricSourceData(LyricSource.NetEaseCloudMusic, 2)
    data class QQMusic(
        val lyric: LyricResult.MusicMusichallSongPlayLyricInfoGetPlayLyricInfo.Data,
        val isQRC: Boolean = true,
        val lrcContent: String? = null
    ) : LyricSourceData(LyricSource.QQMusic, 1)

    data class AM(val lyric: String) : LyricSourceData(LyricSource.AM, 3)
}

enum class LyricSource {
    Empty,
    NetEaseCloudMusic,
    QQMusic,
    AM,
    Custom,
    Kugou,
    Loading
}
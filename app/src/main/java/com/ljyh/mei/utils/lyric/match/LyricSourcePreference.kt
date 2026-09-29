package com.ljyh.mei.utils.lyric.match

/** 用户偏好的歌词来源；[Automatic] 表示按默认兜底顺序（AMLL → 酷狗 → QQ → 网易云）。 */
enum class LyricSourcePreference {
    Automatic,
    AMLL_TTML,
    KUGOU,
    CLOUD_MUSIC,
    QQ_MUSIC;

    val matchSource: LyricMatchSource?
        get() = when (this) {
            Automatic -> null
            AMLL_TTML -> LyricMatchSource.AMLL_TTML
            KUGOU -> LyricMatchSource.KUGOU
            CLOUD_MUSIC -> LyricMatchSource.CLOUD_MUSIC
            QQ_MUSIC -> LyricMatchSource.QQ_MUSIC
        }

    companion object {
        fun fromStorage(value: String?): LyricSourcePreference =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Automatic
    }
}

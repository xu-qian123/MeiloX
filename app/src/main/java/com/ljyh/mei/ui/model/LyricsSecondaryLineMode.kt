package com.ljyh.mei.ui.model

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine

/**
 * 歌词副行显示模式：翻译 / 音译 / 关闭，点击循环切换（移植自 NeriPlayer 的
 * `LyricsSecondaryLineMode`）。只会在当前歌词真正有内容的模式之间循环。
 */
enum class LyricsSecondaryLineMode {
    TRANSLATION,
    PHONETIC,
    NONE,
}

/**
 * 根据副行显示请求（胶囊循环的结果，null = 自动）解析当前应显示的模式。
 *
 * 关键是「按可用性兜底」：用户为某首歌选了音译后，切到一首只有翻译的歌时会自动
 * 退回翻译，而不是整行空掉（旧实现直接改全局翻译/音译开关，正是串味的根因）。
 */
fun resolveLyricsSecondaryLineMode(
    requestedMode: LyricsSecondaryLineMode?,
    translationEnabled: Boolean,
    romanizationEnabled: Boolean,
    hasTranslation: Boolean,
    hasPhonetic: Boolean,
): LyricsSecondaryLineMode {
    val translationAvailable = translationEnabled && hasTranslation
    val phoneticAvailable = romanizationEnabled && hasPhonetic
    return when (requestedMode) {
        LyricsSecondaryLineMode.TRANSLATION -> when {
            translationAvailable -> LyricsSecondaryLineMode.TRANSLATION
            phoneticAvailable -> LyricsSecondaryLineMode.PHONETIC
            else -> LyricsSecondaryLineMode.NONE
        }

        LyricsSecondaryLineMode.PHONETIC -> when {
            phoneticAvailable -> LyricsSecondaryLineMode.PHONETIC
            translationAvailable -> LyricsSecondaryLineMode.TRANSLATION
            else -> LyricsSecondaryLineMode.NONE
        }

        LyricsSecondaryLineMode.NONE -> LyricsSecondaryLineMode.NONE

        // 自动：有音译优先音译，其次翻译。
        null -> when {
            phoneticAvailable -> LyricsSecondaryLineMode.PHONETIC
            translationAvailable -> LyricsSecondaryLineMode.TRANSLATION
            else -> LyricsSecondaryLineMode.NONE
        }
    }
}

/** 循环到下一个可用模式（可用模式 + 关闭）。 */
fun nextLyricsSecondaryLineMode(
    current: LyricsSecondaryLineMode,
    hasTranslation: Boolean,
    hasPhonetic: Boolean,
): LyricsSecondaryLineMode {
    val available = buildList {
        if (hasTranslation) add(LyricsSecondaryLineMode.TRANSLATION)
        if (hasPhonetic) add(LyricsSecondaryLineMode.PHONETIC)
    }
    if (available.isEmpty()) return LyricsSecondaryLineMode.NONE
    val cycle = available + LyricsSecondaryLineMode.NONE
    return cycle[(cycle.indexOf(current) + 1).mod(cycle.size)]
}

/** 当前歌词是否带有可显示的翻译。 */
fun SyncedLyrics.hasDisplayableTranslation(): Boolean =
    lines.any { line -> line.translationForDisplay()?.isNotBlank() == true }

/** 当前歌词是否带有可显示的音译（行级或音节级）。 */
fun SyncedLyrics.hasDisplayablePhonetic(): Boolean =
    lines.any { line -> line.phoneticForDisplay()?.isNotBlank() == true }

/**
 * 生成用于显示的歌词：音译模式把音译放进副行槽位。
 *
 * 经典歌词视图（accompanist-lyrics-ui）的副行只渲染 `translation`，所以音译要显示在
 * 副行就必须走这个转换；翻译模式与关闭模式分别保持原样/清空副行。
 */
fun SyncedLyrics.withSecondaryLineMode(mode: LyricsSecondaryLineMode): SyncedLyrics =
    copy(lines = lines.map { it.withSecondaryLine(mode) })

internal fun ISyncedLine.phoneticForDisplay(): String? = when (this) {
    is KaraokeLine -> phonetic
        ?: syllables.mapNotNull { it.phonetic }.takeIf { it.isNotEmpty() }?.joinToString("")
    else -> null
}

internal fun ISyncedLine.translationForDisplay(): String? = when (this) {
    is KaraokeLine -> translation
    is SyncedLine -> translation
    else -> null
}

private fun ISyncedLine.withSecondaryLine(mode: LyricsSecondaryLineMode): ISyncedLine {
    // 这一行最终要显示的副行文本：翻译 / 音译（没有音译时退回翻译）/ 空。
    val secondary = when (mode) {
        LyricsSecondaryLineMode.TRANSLATION -> translationForDisplay()
        LyricsSecondaryLineMode.PHONETIC ->
            phoneticForDisplay()?.takeIf { it.isNotBlank() } ?: translationForDisplay()

        LyricsSecondaryLineMode.NONE -> null
    }
    // 库的歌词视图会同时渲染行级 phonetic 和 translation，这里统一走 translation 槽位，
    // 并把 phonetic（含音节级）清掉，避免同一段音译渲染两行。
    return when (this) {
        is KaraokeLine.MainKaraokeLine -> copy(
            translation = secondary,
            phonetic = null,
            syllables = syllables.withoutPhonetics(),
            accompanimentLines = accompanimentLines?.map { accompaniment ->
                accompaniment.withSecondaryLine(mode) as KaraokeLine.AccompanimentKaraokeLine
            },
        )

        is KaraokeLine.AccompanimentKaraokeLine -> copy(
            translation = secondary,
            phonetic = null,
            syllables = syllables.withoutPhonetics(),
        )

        is SyncedLine -> copy(translation = secondary)

        else -> this
    }
}

private fun List<com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable>.withoutPhonetics():
    List<com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable> =
    if (none { it.phonetic != null }) {
        this
    } else {
        map { syllable -> if (syllable.phonetic == null) syllable else syllable.copy(phonetic = null) }
    }

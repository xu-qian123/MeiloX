package com.ljyh.mei.utils.lyric

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlin.math.abs

/**
 * 音译（网易云 `romalrc` / QQ `roma` 等）与主歌词的合并工具。
 *
 * 音译文本本身是 LRC 格式，和翻译一样按时间戳贴回主歌词行；超出容差的行视为没有音译
 * （纯音乐段落等），不会硬套上一行。
 */
internal const val ROMANIZATION_MATCH_TOLERANCE_MS = 2_000

internal data class TimedRomanizationLine(val timeMs: Int, val text: String)

private val romanizationLineRegex = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})](.*)")

/** 解析音译 LRC，忽略元数据行与 `//` 注释行。 */
internal fun parseRomanizationLrc(lrc: String?): List<TimedRomanizationLine> {
    if (lrc.isNullOrBlank()) return emptyList()
    return lrc.lineSequence()
        .mapNotNull { raw ->
            val match = romanizationLineRegex.matchEntire(raw.trim()) ?: return@mapNotNull null
            val text = match.groupValues[4].trim()
            if (text.isEmpty() || text.startsWith("//")) return@mapNotNull null
            val time = "${match.groupValues[1]}:${match.groupValues[2]}.${match.groupValues[3]}".parseAsTime()
            TimedRomanizationLine(time, text)
        }
        .sortedBy { it.timeMs }
        .toList()
}

/** 按时间就近取音译文本；超出容差返回 null。 */
internal fun matchRomanizationLine(lines: List<TimedRomanizationLine>, startMs: Int): String? {
    var best: TimedRomanizationLine? = null
    var bestDelta = Int.MAX_VALUE
    for (line in lines) {
        val delta = abs(line.timeMs - startMs)
        if (delta < bestDelta) {
            bestDelta = delta
            best = line
        }
    }
    return best?.takeIf { bestDelta <= ROMANIZATION_MATCH_TOLERANCE_MS }?.text
}

/**
 * 给歌词行挂上行级音译。
 * - 逐字歌词（KaraokeLine）直接写 `phonetic`；
 * - 普通 LRC 行（SyncedLine）没有 phonetic 字段，升级为单音节 KaraokeLine。
 */
internal fun List<ISyncedLine>.withRomanization(romanizationLrc: String?): List<ISyncedLine> {
    val romaLines = parseRomanizationLrc(romanizationLrc)
    if (romaLines.isEmpty()) return this
    return map { line ->
        val text = matchRomanizationLine(romaLines, line.start) ?: return@map line
        when (line) {
            is KaraokeLine.MainKaraokeLine -> line.copy(phonetic = text)
            is KaraokeLine.AccompanimentKaraokeLine -> line
            is SyncedLine -> KaraokeLine.MainKaraokeLine(
                syllables = listOf(KaraokeSyllable(line.content, line.start, line.end)),
                translation = line.translation,
                alignment = KaraokeAlignment.Unspecified,
                start = line.start,
                end = line.end,
                phonetic = text,
            )

            else -> line
        }
    }
}

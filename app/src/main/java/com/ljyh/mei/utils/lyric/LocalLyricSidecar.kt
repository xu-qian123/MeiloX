package com.ljyh.mei.utils.lyric

import java.io.File

/**
 * 本地歌曲的歌词边车文件（与 NeriPlayer 的命名兼容）：
 *
 * - 主歌词：`song.lrc` / `song.txt`
 * - 翻译：`song_trans.lrc`
 * - 音译：`song_roma.lrc` / `song_romalrc.lrc` / `song_romanized.lrc`
 *
 * 查找时按文件名大小写不敏感匹配，均为音频文件同级目录。
 */
internal data class LocalLyricSidecars(
    val lyric: File?,
    val translation: File?,
    val romanization: File?,
)

private val lyricSidecarExtensions = listOf("lrc", "lrc.txt", "txt")

/** 生成边车候选文件名（按优先级排序）。 */
internal fun localLyricSidecarCandidates(audioFileName: String): Map<String, List<String>> {
    val base = audioFileName.substringBeforeLast('.', audioFileName)
    fun names(prefix: String): List<String> = lyricSidecarExtensions.map { ext -> "$prefix.$ext" }
    return mapOf(
        "lyric" to names(base),
        "translation" to names("${base}_trans"),
        "romanization" to listOf("${base}_roma", "${base}_romalrc", "${base}_romanized")
            .flatMap(::names),
    )
}

/** 在音频文件同级目录查找主歌词 / 翻译 / 音译边车。 */
internal fun resolveLocalLyricSidecars(audioPath: String): LocalLyricSidecars {
    val audio = File(audioPath)
    val directory = audio.parentFile ?: return LocalLyricSidecars(null, null, null)
    val filesByName = directory.listFiles()
        ?.filter { it.isFile }
        ?.associateBy { it.name.lowercase() }
        .orEmpty()
    val candidates = localLyricSidecarCandidates(audio.name)
    fun find(kind: String): File? = candidates[kind]
        .orEmpty()
        .firstNotNullOfOrNull { name -> filesByName[name.lowercase()] }
    return LocalLyricSidecars(
        lyric = find("lyric"),
        translation = find("translation"),
        romanization = find("romanization"),
    )
}

/** 读取边车内容；空文件视为不存在。 */
internal fun File.readSidecarText(): String? =
    runCatching { takeIf { it.isFile }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

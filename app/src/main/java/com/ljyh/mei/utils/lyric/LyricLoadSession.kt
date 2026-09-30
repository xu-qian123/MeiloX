package com.ljyh.mei.utils.lyric

/** Main-thread ownership of lyric results, including reloads of the same song. */
internal class LyricLoadSession {
    private var generation = 0L
    var customLyricsActive = false
        private set

    fun begin(): Long {
        customLyricsActive = false
        return ++generation
    }

    fun current(): Long = generation
    fun isCurrent(token: Long): Boolean = token == generation
    fun canApplyRemote(token: Long): Boolean = isCurrent(token) && !customLyricsActive
    fun setCustomLyrics(token: Long, active: Boolean) {
        if (isCurrent(token)) customLyricsActive = active
    }
}

package com.ljyh.mei.ui.model

import androidx.annotation.StringRes
import com.ljyh.mei.R

enum class PlayerAction(
    val id: String,
    @StringRes val labelRes: Int,
    val systemName: String,
) {
    PLAY_MODE("mode", R.string.player_action_play_mode, "repeat"),
    QUEUE("queue", R.string.player_action_queue, "music.note.list"),
    LYRICS("lyrics", R.string.player_action_lyrics, "quote.bubble"),
    SLEEP_TIMER("sleep", R.string.more_action_sleep_timer, "moon"),
    ADD_TO_PLAYLIST("add", R.string.more_action_add_playlist, "text.badge.plus"),
    DOWNLOAD("download", R.string.more_action_download, "arrow.down.circle"),
    MORE("more", R.string.more_actions_title, "ellipsis"),
    ;

    companion object {
        /** 功能栏最多展示的动作数。 */
        const val MAX_VISIBLE = 5

        fun fromSettings(settingString: String): List<PlayerAction> {
            if (settingString.isBlank()) return defaultActions
            return settingString.split(',').mapNotNull { id -> entries.find { it.id == id } }
        }

        fun toSettings(actions: List<PlayerAction>): String = actions.joinToString(",") { it.id }

        /**
         * 一次性迁移：标题栏的「更多」入口移到功能栏后，旧配置若缺少 MORE 自动补一次。
         * 超过上限时去掉末尾一个动作；返回 null 表示无需迁移。
         */
        fun migrateMissingMore(settingString: String): String? {
            val actions = fromSettings(settingString)
            if (actions.contains(MORE)) return null
            val base = if (actions.size >= MAX_VISIBLE) actions.dropLast(1) else actions
            return toSettings(base + MORE)
        }

        val defaultActions = listOf(PLAY_MODE, QUEUE, LYRICS, MORE)
    }
}

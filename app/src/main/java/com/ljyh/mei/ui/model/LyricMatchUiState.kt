package com.ljyh.mei.ui.model

import com.ljyh.mei.utils.lyric.match.RankedLyricMatch

/** UI state for the in-editor multi-source lyric search. */
sealed interface LyricMatchUiState {
    data object Idle : LyricMatchUiState
    data object Loading : LyricMatchUiState
    data class Success(val results: List<RankedLyricMatch>) : LyricMatchUiState
    data class Error(val message: String) : LyricMatchUiState
}

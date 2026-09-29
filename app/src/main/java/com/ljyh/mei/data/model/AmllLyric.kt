package com.ljyh.mei.data.model

/** AMLL TTML DB search hit (`POST /api/search-lyrics`). */
data class AmllLyricSearchResult(
    val file: String,
    val title: String,
    val titles: List<String>,
    val artist: String,
    val artists: List<String>,
    val albums: List<String>,
)

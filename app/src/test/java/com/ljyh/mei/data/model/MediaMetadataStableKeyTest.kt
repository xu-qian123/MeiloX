package com.ljyh.mei.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMetadataStableKeyTest {
    private fun metadata(
        id: Long = 3439302127L,
        albumId: Long = 0L,
        originId: String? = null,
    ) = MediaMetadata(
        id = id,
        title = "难受",
        coverUrl = "",
        artists = emptyList(),
        duration = 0L,
        album = MediaMetadata.Album(id = albumId, title = "无法长大"),
        originId = originId,
    )

    @Test
    fun `album id does not participate in the key`() {
        val missingAlbum = metadata(albumId = 0L)
        val realAlbum = metadata(albumId = 1217753L)
        assertEquals("3439302127", missingAlbum.stableKey())
        assertEquals(missingAlbum.stableKey(), realAlbum.stableKey())
    }

    @Test
    fun `origin id wins over the numeric id`() {
        assertEquals(
            "local_42",
            metadata(id = 7L, originId = "local_42").stableKey(),
        )
    }

    @Test
    fun `blank origin id falls back to the numeric id`() {
        assertEquals("7", metadata(id = 7L, originId = "   ").stableKey())
    }
}

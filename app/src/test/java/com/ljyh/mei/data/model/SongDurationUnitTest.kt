package com.ljyh.mei.data.model

import com.ljyh.mei.data.model.room.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `Song.duration` is stored in milliseconds everywhere it is written
 * (`FavoritesRepository`, `MusicService`, `DownloadWorker`), so converting a
 * cached [Song] back to [MediaMetadata] must not scale it again. A previous
 * `duration * 1000` inflated every queue duration by 1000x.
 */
class SongDurationUnitTest {

    @Test
    fun `cached song duration stays in milliseconds`() {
        val song = Song(
            id = "347230",
            title = "海阔天空",
            artist = listOf("Beyond"),
            album = "乐与怒",
            cover = "",
            duration = 323_000L,
        )

        assertEquals(323_000L, song.toMediaMetadata().duration)
    }
}

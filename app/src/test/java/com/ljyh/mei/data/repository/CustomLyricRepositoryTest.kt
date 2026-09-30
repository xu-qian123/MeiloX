package com.ljyh.mei.data.repository

import com.ljyh.mei.data.model.room.CustomLyric
import com.ljyh.mei.di.dao.CustomLyricDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import java.io.IOException
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 与 DAO 中 `substr(stableKey, 1, instr(stableKey, '|') - 1)` 的语义一致。 */
private fun String.songKeyPart(): String =
    if ('|' in this) substringBefore('|') else ""

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CustomLyricRepositoryTest {
    private val songKey = "3439302127"

    private class FakeCustomLyricDao : CustomLyricDao {
        val rows = linkedMapOf<String, CustomLyric>()
        var afterRead: suspend () -> Unit = {}
        var beforeWrite: suspend () -> Unit = {}

        override suspend fun get(stableKey: String): CustomLyric? = rows[stableKey]

        override fun observe(stableKey: String): Flow<CustomLyric?> = flowOf(rows[stableKey])

        override suspend fun getAllForSongKey(stableKey: String): List<CustomLyric> {
            val snapshot = rows.values
                .filter { row -> row.stableKey == stableKey || row.stableKey.songKeyPart() == stableKey }
                .sortedByDescending { it.updatedAt }
            afterRead()
            return snapshot
        }

        override suspend fun deleteLegacyForSongKey(stableKey: String) {
            rows.keys.removeAll { key -> key != stableKey && key.songKeyPart() == stableKey }
        }

        override suspend fun upsert(entry: CustomLyric) {
            beforeWrite()
            rows[entry.stableKey] = entry
        }

        override suspend fun delete(stableKey: String) {
            rows.remove(stableKey)
        }
    }

    private fun legacyKey(albumId: Long) = "$songKey|$albumId"

    @Test
    fun `offset read overlapping lyric save does not restore old text`() = runTest {
        val dao = FakeCustomLyricDao()
        dao.rows[songKey] = CustomLyric(songKey, lyric = "old", translatedLyric = "old translation")
        val repo = CustomLyricRepository(dao)
        val resume = CompletableDeferred<Unit>()
        var firstRead = true
        dao.afterRead = { if (firstRead) { firstRead = false; resume.await() } }
        val offset = launch { repo.saveOffset(songKey, 150L) }
        runCurrent()
        val edit = launch { repo.saveLyric(songKey, "edited", "edited translation") }
        runCurrent()
        resume.complete(Unit)
        joinAll(offset, edit)
        val persisted = CustomLyricRepository(dao).get(songKey)
        assertEquals("edited", persisted?.lyric)
        assertEquals("edited translation", persisted?.translatedLyric)
        assertEquals(150L, persisted?.userLyricOffsetMs)
    }

    @Test
    fun `offset overlapping restore cannot resurrect deleted lyrics`() = runTest {
        val dao = FakeCustomLyricDao()
        dao.rows[songKey] = CustomLyric(songKey, lyric = "custom")
        val repo = CustomLyricRepository(dao)
        val resume = CompletableDeferred<Unit>()
        var firstRead = true
        dao.afterRead = { if (firstRead) { firstRead = false; resume.await() } }
        val offset = launch { repo.saveOffset(songKey, 200L) }
        runCurrent()
        val clear = launch { repo.clearLyric(songKey) }
        runCurrent()
        resume.complete(Unit)
        joinAll(offset, clear)
        assertNull(repo.get(songKey)?.lyric)
        assertEquals(200L, repo.get(songKey)?.userLyricOffsetMs)
    }

    @Test
    fun `read failure cannot turn an offset update into lyric deletion`() = runTest {
        val dao = FakeCustomLyricDao()
        val original = CustomLyric(songKey, lyric = "saved")
        dao.rows[songKey] = original
        val error = IOException("read failed")
        dao.afterRead = { throw error }
        try {
            CustomLyricRepository(dao).saveOffset(songKey, 100)
            fail("Read failure must propagate")
        } catch (actual: IOException) { assertSame(error, actual) }
        assertEquals(original, dao.rows[songKey])
    }

    @Test
    fun `migration write failure leaves legacy original available`() = runTest {
        val dao = FakeCustomLyricDao()
        val original = lyricRow(legacyKey(0), "saved", 1)
        dao.rows[original.stableKey] = original
        dao.beforeWrite = { throw IOException("disk full") }
        try {
            CustomLyricRepository(dao).get(songKey)
            fail("Migration must report failure")
        } catch (_: IOException) { }
        assertEquals(listOf(original), dao.rows.values.toList())
    }

    @Test
    fun `cancelled read does not write a partial record`() = runTest {
        val dao = FakeCustomLyricDao()
        val original = CustomLyric(songKey, lyric = "saved")
        dao.rows[songKey] = original
        dao.afterRead = { throw CancellationException("cancelled") }
        try {
            CustomLyricRepository(dao).saveLyric(songKey, "new", null)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(original, dao.rows[songKey])
    }

    @Test
    fun `clearing offset retains persisted original and translation`() = runTest {
        val dao = FakeCustomLyricDao()
        val repo = CustomLyricRepository(dao)
        repo.saveLyric(songKey, "original", "translation")
        repo.saveOffset(songKey, -200)
        repo.clearOffset(songKey)
        val reloaded = CustomLyricRepository(dao).get(songKey)
        assertEquals("original", reloaded?.lyric)
        assertEquals("translation", reloaded?.translatedLyric)
        assertNull(reloaded?.userLyricOffsetMs)
    }

    private fun lyricRow(key: String, lyric: String, updatedAt: Long) =
        CustomLyric(stableKey = key, lyric = lyric, updatedAt = updatedAt)

    @Test
    fun `legacy key rows are migrated to the stable key on read`() = runTest {
        val dao = FakeCustomLyricDao()
        dao.rows[legacyKey(0L)] = lyricRow(legacyKey(0L), "用户编辑的歌词", updatedAt = 100L)
        val repository = CustomLyricRepository(dao)

        val entry = repository.get(songKey)

        assertEquals("用户编辑的歌词", entry?.lyric)
        assertEquals(songKey, entry?.stableKey)
        assertEquals(listOf(songKey), dao.rows.keys.toList())
    }

    @Test
    fun `the most recently updated legacy row wins and duplicates are removed`() = runTest {
        val dao = FakeCustomLyricDao()
        dao.rows[legacyKey(0L)] = lyricRow(legacyKey(0L), "旧歌词", updatedAt = 100L)
        dao.rows[legacyKey(1217753L)] = lyricRow(legacyKey(1217753L), "新歌词", updatedAt = 200L)
        val repository = CustomLyricRepository(dao)

        val entry = repository.get(songKey)

        assertEquals("新歌词", entry?.lyric)
        assertEquals(listOf(songKey), dao.rows.keys.toList())
    }

    @Test
    fun `saving after a legacy read keeps a single row`() = runTest {
        val dao = FakeCustomLyricDao()
        dao.rows[legacyKey(0L)] = lyricRow(legacyKey(0L), "旧歌词", updatedAt = 100L)
        val repository = CustomLyricRepository(dao)

        repository.saveLyric(stableKey = songKey, lyric = "改过的歌词", translatedLyric = null)

        assertEquals(listOf(songKey), dao.rows.keys.toList())
        assertEquals("改过的歌词", dao.rows[songKey]?.lyric)
    }

    @Test
    fun `clearing does not resurrect legacy rows`() = runTest {
        val dao = FakeCustomLyricDao()
        dao.rows[legacyKey(0L)] = lyricRow(legacyKey(0L), "用户编辑的歌词", updatedAt = 100L)
        val repository = CustomLyricRepository(dao)

        repository.clearLyric(songKey)

        assertTrue(dao.rows.isEmpty())
        assertNull(repository.get(songKey))
    }

    @Test
    fun `saving without any previous row writes the stable key only`() = runTest {
        val dao = FakeCustomLyricDao()
        val repository = CustomLyricRepository(dao)

        repository.saveLyric(stableKey = songKey, lyric = "新编辑", translatedLyric = null)

        assertEquals(listOf(songKey), dao.rows.keys.toList())
    }

    @Test
    fun `offset survives a legacy migration`() = runTest {
        val dao = FakeCustomLyricDao()
        dao.rows[legacyKey(0L)] = lyricRow(legacyKey(0L), "旧歌词", updatedAt = 100L)
            .copy(userLyricOffsetMs = -250L)
        val repository = CustomLyricRepository(dao)

        repository.saveOffset(songKey, 120L)

        assertEquals(1, dao.rows.size)
        assertEquals(120L, dao.rows[songKey]?.userLyricOffsetMs)
        assertEquals("旧歌词", dao.rows[songKey]?.lyric)
    }

    @Test
    fun `selectLatestCustomLyric picks the newest entry`() {
        val older = lyricRow("a", "old", updatedAt = 1L)
        val newer = lyricRow("b", "new", updatedAt = 2L)
        assertEquals(newer, selectLatestCustomLyric(listOf(older, newer)))
        assertNull(selectLatestCustomLyric(emptyList()))
    }
}

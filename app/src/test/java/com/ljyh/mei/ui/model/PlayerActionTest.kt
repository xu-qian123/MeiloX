package com.ljyh.mei.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerActionTest {
    @Test
    fun `default toolbar already contains the more action`() {
        assertTrue(PlayerAction.defaultActions.contains(PlayerAction.MORE))
        assertNull(PlayerAction.migrateMissingMore(PlayerAction.toSettings(PlayerAction.defaultActions)))
    }

    @Test
    fun `legacy configuration without more gets it appended once`() {
        val migrated = PlayerAction.migrateMissingMore("mode,queue,lyrics")
        assertEquals("mode,queue,lyrics,more", migrated)
        // 迁移结果已经包含 MORE，再次迁移应返回 null（幂等）。
        assertNull(PlayerAction.migrateMissingMore(migrated!!))
    }

    @Test
    fun `append respects the visible limit by dropping the last legacy action`() {
        val migrated = PlayerAction.migrateMissingMore("mode,queue,lyrics,sleep,download")
        assertEquals("mode,queue,lyrics,sleep,more", migrated)
    }

    @Test
    fun `blank configuration uses defaults and needs no migration`() {
        assertNull(PlayerAction.migrateMissingMore(""))
    }
}

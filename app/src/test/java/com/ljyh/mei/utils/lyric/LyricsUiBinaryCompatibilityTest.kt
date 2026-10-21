package com.ljyh.mei.utils.lyric

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsUiBinaryCompatibilityTest {
    @Test
    fun syllableRetainsTheCopySignatureUsedByLyricsUi1019() {
        // lyrics-ui is precompiled: source-level copy() tests cannot detect ABI breakage.
        val copy = KaraokeSyllable::class.java.getDeclaredMethod(
            "copy\$default",
            KaraokeSyllable::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType,
            Any::class.java,
        )
        val original = KaraokeSyllable("word ", 100, 300, "phonetic")
        val trimmed = copy.invoke(null, original, "word", 0, 0, null, 14, null) as KaraokeSyllable
        assertEquals(original.copy(content = "word"), trimmed)
    }
}

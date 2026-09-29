package com.ljyh.mei.utils.lyric.match

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextMatcherTest {

    @Test
    fun exactMatchBeatsPrefixMatch() {
        val exact = SearchTextMatcher.score("hello", listOf("hello"))!!
        val prefix = SearchTextMatcher.score("hello", listOf("hello world"))!!
        assertTrue(exact < prefix)
    }

    @Test
    fun substringMatches() {
        assertNotNull(SearchTextMatcher.score("love", listOf("My Love Song")))
    }

    @Test
    fun pinyinFullSpellingMatchesChinese() {
        assertNotNull(SearchTextMatcher.score("zhoujielun", listOf("周杰伦")))
    }

    @Test
    fun pinyinInitialsMatch() {
        assertNotNull(SearchTextMatcher.score("zjl", listOf("周杰伦")))
    }

    @Test
    fun unrelatedAsciiQueryDoesNotMatchChineseText() {
        assertNull(SearchTextMatcher.score("zzzz", listOf("周杰伦")))
    }

    @Test
    fun blankQueryScoresZero() {
        assertTrue(SearchTextMatcher.score("   ", listOf("anything")) == 0)
    }
}

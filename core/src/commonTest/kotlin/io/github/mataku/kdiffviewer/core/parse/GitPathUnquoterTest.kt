package io.github.mataku.kdiffviewer.core.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitPathUnquoterTest {
    @Test
    fun unquotesEscapes() {
        assertEquals("a\\b\"c\td\n", unquoteGitPath("\"a\\\\b\\\"c\\td\\n\""))
    }

    @Test
    fun unquotesOctalUtf8() {
        assertEquals("日", unquoteGitPath("\"\\346\\227\\245\""))
    }

    @Test
    fun unquotedPassesThrough() {
        assertEquals("plain/path.txt", unquoteGitPath("plain/path.txt"))
    }

    @Test
    fun headerWithSpaces() {
        assertEquals("x y" to "x y", parseDiffGitHeaderPaths("a/x y b/x y"))
    }

    @Test
    fun headerQuotedPair() {
        assertEquals("tab\there" to "tab\there", parseDiffGitHeaderPaths("\"a/tab\\there\" \"b/tab\\there\""))
    }

    @Test
    fun headerWithDifferentPaths() {
        assertEquals("old.txt" to "new.txt", parseDiffGitHeaderPaths("a/old.txt b/new.txt"))
    }

    @Test
    fun headerUnrecognizedIsNull() {
        assertNull(parseDiffGitHeaderPaths("garbage"))
    }
}

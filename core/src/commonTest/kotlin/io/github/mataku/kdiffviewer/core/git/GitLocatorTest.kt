package io.github.mataku.kdiffviewer.core.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitLocatorTest {
    @Test
    fun firstExecutableInPathOrderWins() {
        assertEquals("/b/git", GitLocator.locate("/a:/b") { it == "/b/git" })
        assertEquals("/a/git", GitLocator.locate("/a:/b") { true })
    }

    @Test
    fun emptySegmentsAreIgnored() {
        val checked = mutableListOf<String>()
        GitLocator.locate(":/a::") { checked += it; false }
        assertEquals(listOf("/a/git") + GitLocator.FALLBACK_PATHS, checked)
    }

    @Test
    fun trailingSlashDoesNotDoubleSlash() {
        assertEquals("/a/git", GitLocator.locate("/a/") { it == "/a/git" })
    }

    @Test
    fun nullPathFallsBackInOrder() {
        assertEquals(
            listOf("/opt/homebrew/bin/git", "/usr/local/bin/git", "/usr/bin/git"),
            GitLocator.FALLBACK_PATHS,
        )
        assertEquals("/opt/homebrew/bin/git", GitLocator.locate(null) { true })
        assertEquals("/usr/local/bin/git", GitLocator.locate(null) { it != "/opt/homebrew/bin/git" })
        assertEquals("/usr/bin/git", GitLocator.locate(null) { it == "/usr/bin/git" })
    }

    @Test
    fun returnsNullWhenNothingIsExecutable() {
        assertNull(GitLocator.locate("/a", { false }))
    }
}

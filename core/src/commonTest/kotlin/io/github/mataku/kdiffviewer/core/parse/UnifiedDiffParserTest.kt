package io.github.mataku.kdiffviewer.core.parse

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.DiffStats
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.stats
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UnifiedDiffParserTest {
    private suspend fun parse(text: String): List<FileDiff> =
        UnifiedDiffParser.parse(splitLines(text).asFlow()).toList()

    @Test
    fun modifiedFileWithTwoHunks() = runTest {
        val files = parse(
            """
            diff --git a/src/a.kt b/src/a.kt
            index 1111111..2222222 100644
            --- a/src/a.kt
            +++ b/src/a.kt
            @@ -1,3 +1,3 @@ fun main()
             one
            -two
            +TWO
             three
            @@ -10 +10,2 @@
             ten
            +eleven
            """.trimIndent() + "\n",
        )
        val file = files.single()
        assertEquals("src/a.kt", file.oldPath)
        assertEquals("src/a.kt", file.newPath)
        assertEquals(ChangeType.Modified, file.change)
        assertFalse(file.isUntracked)
        val hunks = (file.content as DiffContent.Text).hunks
        assertEquals(2, hunks.size)
        assertEquals(" fun main()", hunks[0].header)
        assertEquals(
            listOf(
                DiffLine(LineKind.Context, "one", 1, 1),
                DiffLine(LineKind.Removed, "two", 2, null),
                DiffLine(LineKind.Added, "TWO", null, 2),
                DiffLine(LineKind.Context, "three", 3, 3),
            ),
            hunks[0].lines,
        )
        assertEquals(
            Hunk(
                10, 1, 10, 2, "",
                listOf(DiffLine(LineKind.Context, "ten", 10, 10), DiffLine(LineKind.Added, "eleven", null, 11)),
            ),
            hunks[1],
        )
        assertEquals(DiffStats(2, 1), file.stats)
    }

    @Test
    fun twoFilesAreEmittedInOrder() = runTest {
        val files = parse(
            """
            diff --git a/first.txt b/first.txt
            index 1111111..2222222 100644
            --- a/first.txt
            +++ b/first.txt
            @@ -1 +1 @@
            -a
            +b
            diff --git a/second.txt b/second.txt
            index 3333333..4444444 100644
            --- a/second.txt
            +++ b/second.txt
            @@ -1 +1 @@
            -c
            +d
            """.trimIndent() + "\n",
        )
        assertEquals(listOf("first.txt", "second.txt"), files.map { it.newPath })
        assertEquals(listOf("first.txt", "second.txt"), files.map { it.oldPath })
    }

    @Test
    fun noNewlineMarkerHasNoLineNumbers() = runTest {
        val files = parse(
            """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1 +1 @@
            -a
            \ No newline at end of file
            +b
            """.trimIndent() + "\n",
        )
        val lines = (files.single().content as DiffContent.Text).hunks.single().lines
        assertEquals(DiffLine(LineKind.NoNewlineAtEof, " No newline at end of file", null, null), lines[1])
        assertEquals(DiffStats(1, 1), files.single().stats)
    }

    @Test
    fun emptyInputEmitsNothing() = runTest {
        assertEquals(emptyList(), parse(""))
    }

    @Test
    fun splitLinesKeepsLoneCarriageReturn() {
        assertEquals(listOf("a", "b\rc"), splitLines("a\r\nb\rc\n"))
    }
}

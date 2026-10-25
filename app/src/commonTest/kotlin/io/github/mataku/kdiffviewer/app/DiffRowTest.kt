package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.DiffStats
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DiffRowTest {
    private val added = DiffLine(LineKind.Added, "new", null, 1)
    private val removed = DiffLine(LineKind.Removed, "old", 1, null)

    private fun textFile(path: String = "a.txt") = FileDiff(
        oldPath = path,
        newPath = path,
        change = ChangeType.Modified,
        isUntracked = false,
        content = DiffContent.Text(
            listOf(Hunk(1, 1, 1, 1, "@@ -1 +1 @@", listOf(removed, added))),
        ),
    )

    private fun fileWith(content: DiffContent) = FileDiff("a.bin", "a.bin", ChangeType.Modified, false, content)

    @Test
    fun flattensHeaderHunksAndLines() {
        val file = textFile()
        val rows = flattenRows(listOf(file))
        assertEquals(
            listOf(
                DiffRow.FileHeader(0, file, DiffStats(1, 1)),
                DiffRow.HunkHeader(0, 0, (file.content as DiffContent.Text).hunks[0]),
                DiffRow.Line(0, 0, 0, removed),
                DiffRow.Line(0, 0, 1, added),
            ),
            rows,
        )
    }

    @Test
    fun keysAreIndexBasedAndUnique() {
        val rows = flattenRows(listOf(textFile(), textFile()))
        assertEquals(
            listOf("f0", "h0-0", "l0-0-0", "l0-0-1", "f1", "h1-0", "l1-0-0", "l1-0-1"),
            rows.map { it.key },
        )
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun startIndexOffsetsFileIndex() {
        assertEquals("f3", flattenRows(listOf(textFile()), startIndex = 3).first().key)
    }

    @Test
    fun placeholders() {
        fun placeholder(content: DiffContent): DiffRow.Placeholder {
            val rows = flattenRows(listOf(fileWith(content)))
            assertEquals(2, rows.size)
            assertIs<DiffRow.FileHeader>(rows[0])
            return assertIs<DiffRow.Placeholder>(rows[1])
        }
        assertEquals("Binary file not shown", placeholder(DiffContent.Binary).message)
        assertEquals("File too large to display", placeholder(DiffContent.TooLarge).message)
        assertEquals("Could not parse this diff", placeholder(DiffContent.Unparseable).message)
        assertEquals("p0", placeholder(DiffContent.Binary).key)
    }

    @Test
    fun contentlessTextHasOnlyHeader() {
        val rows = flattenRows(listOf(fileWith(DiffContent.Text(emptyList()))))
        assertEquals(1, rows.size)
        assertIs<DiffRow.FileHeader>(rows[0])
    }
}

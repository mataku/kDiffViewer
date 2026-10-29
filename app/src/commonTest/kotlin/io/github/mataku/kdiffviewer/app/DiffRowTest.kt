package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.DiffStats
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.GapView
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
  fun largeDiffCountsChangedLinesAgainstThreshold() {
    val context = DiffLine(LineKind.Context, "ctx", 2, 2)
    val file = FileDiff(
      "a.txt",
      "a.txt",
      ChangeType.Modified,
      false,
      DiffContent.Text(listOf(Hunk(1, 6, 1, 7, "@@", listOf(removed, added, added.copy(newLineNo = 2)) + List(5) { context }))),
    )
    assertEquals(true, (flattenRows(listOf(file), largeDiffLines = 3)[0] as DiffRow.FileHeader).largeDiff)
    assertEquals(false, (flattenRows(listOf(file), largeDiffLines = 4)[0] as DiffRow.FileHeader).largeDiff)
  }

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

  @Test
  fun tooManyLinesShowsPlaceholderAndIsNotLargeDiff() {
    val rows = flattenRows(listOf(fileWith(DiffContent.TooManyLines(DiffStats(30_000, 0)))), largeDiffLines = 1)
    val header = rows[0] as DiffRow.FileHeader
    assertEquals(DiffStats(30_000, 0), header.stats)
    assertEquals(false, header.largeDiff)
    assertEquals(DiffRow.Placeholder(0, "Diff too large to display"), rows[1])
  }

  @Test
  fun flattenRowsAttachesGapsAndTrailingRow() {
    fun context(n: Int) = DiffLine(LineKind.Context, "L$n", n, n)
    val hunk = { at: Int -> Hunk(at - 3, 7, at - 3, 7, "", listOf(context(at - 3), context(at - 2), context(at - 1), DiffLine(LineKind.Removed, "o", at, null), DiffLine(LineKind.Added, "L$at", null, at), context(at + 1), context(at + 2), context(at + 3))) }
    val file = FileDiff("f.kt", "f.kt", ChangeType.Modified, false, DiffContent.Text(listOf(hunk(11), hunk(40))))
    val rows = flattenRows(listOf(file))
    val headers = rows.filterIsInstance<DiffRow.HunkHeader>()
    assertEquals(listOf(GapView(0, 7, canUp = true, canDown = false), GapView(1, 22, canUp = true, canDown = true)), headers.map { it.gap })
    val trailing = assertIs<DiffRow.TrailingGap>(rows.last())
    assertEquals(GapView(2, null, canUp = false, canDown = true), trailing.gap)
    assertEquals("t0", trailing.key)
  }

  @Test
  fun flattenRowsOmitsTrailingRowWithoutTrailingGap() {
    assertIs<DiffRow.Line>(flattenRows(listOf(textFile())).last())
  }
}

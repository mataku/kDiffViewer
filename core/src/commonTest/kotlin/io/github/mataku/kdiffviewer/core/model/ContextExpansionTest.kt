package io.github.mataku.kdiffviewer.core.model

import io.github.mataku.kdiffviewer.core.parse.splitLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContextExpansionTest {
  private val text = List(60) { "L${it + 1}" }

  private fun context(new: Int, delta: Int = 0) = DiffLine(LineKind.Context, "L$new", new + delta, new)

  private fun modified(path: String = "f.kt", vararg hunks: Hunk) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(hunks.toList()))

  private fun replaceHunk(line: Int, delta: Int = 0): Hunk {
    val lines = listOf(
      context(line - 3, delta),
      context(line - 2, delta),
      context(line - 1, delta),
      DiffLine(LineKind.Removed, "old$line", line + delta, null),
      DiffLine(LineKind.Added, "L$line", null, line),
      context(line + 1, delta),
      context(line + 2, delta),
      context(line + 3, delta),
    )
    return Hunk(line - 3 + delta, 7, line - 3, 7, "fun f$line()", lines)
  }

  private fun twoHunks() = modified("f.kt", replaceHunk(11), replaceHunk(40))

  @Test
  fun gapsForTwoHunks() {
    assertEquals(listOf(1..7, 15..36, null), gapsOf(twoHunks(), null))
  }

  @Test
  fun trailingGapKnownOnceCountIsKnown() {
    assertEquals(44..60, gapsOf(twoHunks(), 60).last())
  }

  @Test
  fun trailingGapAbsentWhenLastHunkHasShortContext() {
    val hunk = Hunk(8, 5, 8, 5, "", listOf(context(8), context(9), context(10), DiffLine(LineKind.Removed, "x", 11, null), DiffLine(LineKind.Added, "L11", null, 11), context(12)))
    val file = modified("f.kt", hunk)
    assertFalse(file.hasTrailingGap())
    assertNull(expandContext(file, null, null).trailingGap)
  }

  @Test
  fun trailingGapAbsentAfterNoNewlineAtEof() {
    val lines = listOf(context(8), context(9), context(10), DiffLine(LineKind.Added, "L11", null, 11), context(12), context(13), context(14), DiffLine(LineKind.NoNewlineAtEof, "", null, null))
    val file = modified("f.kt", Hunk(8, 6, 8, 7, "", lines))
    assertFalse(file.hasTrailingGap())
  }

  @Test
  fun unexpandedFileKeepsInstanceAndReportsGaps() {
    val file = twoHunks()
    val expanded = expandContext(file, null, null)
    assertSame(file, expanded.file)
    assertEquals(GapView(0, 7, canUp = true, canDown = false), expanded.gapBefore[0])
    assertEquals(GapView(1, 22, canUp = true, canDown = true), expanded.gapBefore[1])
    assertEquals(GapView(2, null, canUp = false, canDown = true), expanded.trailingGap)
  }

  @Test
  fun revealDownTakesTwentyFromTop() {
    assertEquals(listOf(15..34), null.reveal(twoHunks(), 1, ExpandDirection.Down, 60).revealed)
  }

  @Test
  fun revealUpTakesTwentyFromBottom() {
    assertEquals(listOf(17..36), null.reveal(twoHunks(), 1, ExpandDirection.Up, 60).revealed)
  }

  @Test
  fun revealAllTakesWholeGap() {
    assertEquals(listOf(15..36), null.reveal(twoHunks(), 1, ExpandDirection.All, 60).revealed)
  }

  @Test
  fun repeatedRevealContinuesAndClamps() {
    val file = twoHunks()
    val once = null.reveal(file, 1, ExpandDirection.Down, 60)
    val twice = once.reveal(file, 1, ExpandDirection.Down, 60)
    assertEquals(listOf(15..36), twice.revealed)
    assertEquals(60, twice.newLineCount)
  }

  @Test
  fun leadingGapRevealsUpward() {
    assertEquals(listOf(1..7), null.reveal(twoHunks(), 0, ExpandDirection.Up, 60).revealed)
  }

  @Test
  fun expandAddsContextWithBothLineNumbers() {
    val file = modified("f.kt", Hunk(8, 6, 8, 7, "", listOf(context(8), context(9), context(10), DiffLine(LineKind.Added, "L11", null, 11), context(12), context(13), context(14)).map { if (it.newLineNo!! > 11) it.copy(oldLineNo = it.newLineNo!! - 1) else it }), replaceHunk(40, delta = -1))
    val expansion = null.reveal(file, 1, ExpandDirection.Down, 60)
    val hunks = (expandContext(file, expansion, text).file.content as DiffContent.Text).hunks
    val revealed = hunks[0].lines.first { it.newLineNo == 20 }
    assertEquals(DiffLine(LineKind.Context, "L20", 19, 20), revealed)
  }

  @Test
  fun partialRevealKeepsTwoHunksAndReportsHidden() {
    val file = twoHunks()
    val expanded = expandContext(file, null.reveal(file, 1, ExpandDirection.Down, 60), text)
    val hunks = (expanded.file.content as DiffContent.Text).hunks
    assertEquals(2, hunks.size)
    assertEquals(Hunk(8, 27, 8, 27, "fun f11()", hunks[0].lines), hunks[0])
    assertEquals((8..34).toList(), hunks[0].lines.mapNotNull { it.newLineNo })
    assertEquals(GapView(1, 2, canUp = true, canDown = true), expanded.gapBefore[1])
  }

  @Test
  fun fullyRevealedGapMergesHunks() {
    val file = twoHunks()
    val expanded = expandContext(file, null.reveal(file, 1, ExpandDirection.All, 60), text)
    val hunks = (expanded.file.content as DiffContent.Text).hunks
    assertEquals(1, hunks.size)
    assertEquals(8, hunks[0].newStart)
    assertEquals(36, hunks[0].newCount)
    assertEquals(36, hunks[0].oldCount)
    assertEquals("fun f11()", hunks[0].header)
    assertEquals((8..43).toList(), hunks[0].lines.mapNotNull { it.newLineNo })
    assertEquals(listOf(GapView(0, 7, canUp = true, canDown = false)), expanded.gapBefore)
  }

  @Test
  fun leadingRevealPrependsToFirstHunk() {
    val file = twoHunks()
    val expanded = expandContext(file, null.reveal(file, 0, ExpandDirection.Up, 60), text)
    val first = (expanded.file.content as DiffContent.Text).hunks[0]
    assertEquals(1, first.newStart)
    assertEquals(1, first.oldStart)
    assertNull(expanded.gapBefore[0])
  }

  @Test
  fun trailingRevealAppendsAndDisappearsAtEof() {
    val file = twoHunks()
    val expanded = expandContext(file, null.reveal(file, 2, ExpandDirection.Down, 60), text)
    val last = (expanded.file.content as DiffContent.Text).hunks.last()
    assertEquals(60, last.lines.last().newLineNo)
    assertNull(expanded.trailingGap)
  }

  @Test
  fun trailingGapWithKnownCountAndNothingRevealedReportsSize() {
    val file = twoHunks()
    val expanded = expandContext(file, ContextExpansion(emptyList(), 60), text)
    assertEquals(GapView(2, 17, canUp = false, canDown = true), expanded.trailingGap)
  }

  @Test
  fun zeroCountHunkStartsAreAdjusted() {
    val insertion = Hunk(10, 0, 11, 1, "", listOf(DiffLine(LineKind.Added, "L11", null, 11)))
    val file = modified("f.kt", insertion)
    assertEquals(1..10, gapsOf(file, null)[0])
    val expanded = expandContext(file, null.reveal(file, 0, ExpandDirection.Up, 60), text)
    val first = (expanded.file.content as DiffContent.Text).hunks[0]
    assertEquals(DiffLine(LineKind.Context, "L10", 10, 10), first.lines[first.lines.size - 2])
  }

  @Test
  fun contextMatchesDetectsChangedLine() {
    assertTrue(contextMatches(twoHunks(), text))
    assertFalse(contextMatches(twoHunks(), text.toMutableList().also { it[8] = "changed" }))
    assertFalse(contextMatches(twoHunks(), text.take(20)))
  }

  @Test
  fun contextMatchesIgnoresCrlf() {
    val file = modified("f.kt", Hunk(1, 2, 1, 2, "", listOf(DiffLine(LineKind.Context, "a", 1, 1), DiffLine(LineKind.Context, "b", 2, 2))))
    assertTrue(contextMatches(file, splitLines("a\r\nb\r\n")))
  }

  @Test
  fun addedAndBinaryFilesHaveNoGaps() {
    val added = FileDiff(null, "a.kt", ChangeType.Added, true, DiffContent.Text(listOf(Hunk(0, 0, 1, 2, "", listOf(DiffLine(LineKind.Added, "a", null, 1), DiffLine(LineKind.Added, "b", null, 2))))))
    val expanded = expandContext(added, null, null)
    assertEquals(listOf(null), expanded.gapBefore)
    assertNull(expanded.trailingGap)
    val binary = FileDiff("b", "b", ChangeType.Modified, false, DiffContent.Binary)
    assertEquals(emptyList(), expandContext(binary, null, null).gapBefore)
  }
}

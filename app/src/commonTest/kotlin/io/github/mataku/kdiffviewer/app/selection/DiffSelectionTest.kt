package io.github.mataku.kdiffviewer.app.selection

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiffSelectionTest {
  private val rows = rowsOf(
    textFile(
      "a.kt",
      hunk(context("one"), DiffLine(LineKind.NoNewlineAtEof, "\\ No newline at end of file", null, null), context("three")),
    ),
    FileDiff(null, "x.bin", ChangeType.Added, false, DiffContent.Binary),
  )

  @Test
  fun startAndEndAreOrdered() {
    val selection = DiffSelection(TextPosition(5, 3), TextPosition(2, 1))
    assertEquals(TextPosition(2, 1), selection.start)
    assertEquals(TextPosition(5, 3), selection.end)
  }

  @Test
  fun isEmptyWhenAnchorEqualsFocus() {
    assertTrue(DiffSelection(TextPosition(1, 2), TextPosition(1, 2)).isEmpty)
    assertFalse(DiffSelection(TextPosition(1, 2), TextPosition(1, 3)).isEmpty)
  }

  @Test
  fun rangeForFirstMiddleLastAndOutside() {
    val selection = DiffSelection(TextPosition(2, 3), TextPosition(4, 2))
    assertEquals(3 until 10, selection.rangeFor(2, 10))
    assertEquals(0 until 10, selection.rangeFor(3, 10))
    assertEquals(0 until 2, selection.rangeFor(4, 10))
    assertNull(selection.rangeFor(1, 10))
    assertNull(selection.rangeFor(5, 10))
  }

  @Test
  fun rangeForEndAtColumnZeroIsNull() {
    val selection = DiffSelection(TextPosition(2, 0), TextPosition(3, 0))
    assertNull(selection.rangeFor(3, 10))
    assertEquals(0 until 10, selection.rangeFor(2, 10))
  }

  @Test
  fun positionAtOrAfterSkipsHeaders() {
    assertEquals(TextPosition(2, 0), rows.positionAtOrAfter(0))
    assertEquals(TextPosition(2, 0), rows.positionAtOrAfter(1))
    assertEquals(TextPosition(4, 0), rows.positionAtOrAfter(3))
  }

  @Test
  fun positionAtOrAfterFallsBackToPreviousEnd() {
    assertEquals(TextPosition(4, 5), rows.positionAtOrAfter(5))
    assertNull(rowsOf().positionAtOrAfter(0))
  }

  @Test
  fun positionAtOrAfterHonorsSide() {
    val mixed = rowsOf(textFile("a.kt", hunk(context("a"), DiffLine(LineKind.Removed, "b", 2, null), DiffLine(LineKind.Added, "c", null, 2))))
    assertEquals(TextPosition(3, 0), mixed.positionAtOrAfter(3))
    assertEquals(TextPosition(3, 0), mixed.positionAtOrAfter(3, Side.Old))
    assertEquals(TextPosition(4, 0), mixed.positionAtOrAfter(3, Side.New))
    assertEquals(TextPosition(3, 1), mixed.positionAtOrAfter(5, Side.Old))
    assertEquals(listOf(true, true, true), (2..4).map { mixed.selectableRow(it, null) })
    assertEquals(listOf(true, true, false), (2..4).map { mixed.selectableRow(it, Side.Old) })
    assertEquals(listOf(true, false, true), (2..4).map { mixed.selectableRow(it, Side.New) })
    assertEquals(4, mixed.nextSelectableRow(2, Side.New))
    assertNull(mixed.nextSelectableRow(3, Side.Old))
  }

  @Test
  fun nextSelectableRowSkipsNoNewline() {
    assertEquals(2, rows.nextSelectableRow(0))
    assertEquals(4, rows.nextSelectableRow(2))
    assertNull(rows.nextSelectableRow(4))
  }

  @Test
  fun selectableRowExcludesHeadersNoNewlineAndPlaceholders() {
    assertEquals(listOf(false, false, true, false, true, false, false), rows.indices.map(rows::selectableRow))
  }
}

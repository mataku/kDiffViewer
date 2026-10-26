package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineSelectionTest {
  private fun hunk(vararg lines: DiffLine) = Hunk(1, lines.size, 1, lines.size, "", lines.toList())

  private fun file(path: String, vararg hunks: Hunk) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(hunks.toList()))

  // 0 header a, 1 hunk, 2 ctx, 3 removed, 4 added, 5 hunk, 6 added, 7 no-newline,
  // 8 header b, 9 hunk, 10 ctx, 11 header c (binary), 12 placeholder
  private val rows = flattenRows(
    listOf(
      file(
        "a",
        hunk(
          DiffLine(LineKind.Context, "keep", 1, 1),
          DiffLine(LineKind.Removed, "old", 2, null),
          DiffLine(LineKind.Added, "\tnew", null, 2),
        ),
        hunk(
          DiffLine(LineKind.Added, "tail", null, 10),
          DiffLine(LineKind.NoNewlineAtEof, "", null, null),
        ),
      ),
      file("b", hunk(DiffLine(LineKind.Context, "other", 1, 1))),
      FileDiff("c", "c", ChangeType.Modified, false, DiffContent.Binary),
    ),
  )

  @Test
  fun clickSelectsSingleLine() {
    assertEquals(LineSelection(3, 3, 3), null.afterLineNumberClick(rows, 3, extend = false))
  }

  @Test
  fun clickOnSelectedSingleLineDeselects() {
    assertNull(LineSelection(3, 3, 3).afterLineNumberClick(rows, 3, extend = false))
  }

  @Test
  fun clickOnAnotherLineReplacesSelection() {
    assertEquals(LineSelection(4, 4, 4), LineSelection(2, 2, 3).afterLineNumberClick(rows, 4, extend = false))
  }

  @Test
  fun clickInsideRangeSelectsThatLine() {
    assertEquals(LineSelection(3, 3, 3), LineSelection(2, 2, 4).afterLineNumberClick(rows, 3, extend = false))
  }

  @Test
  fun shiftClickExtendsFromAnchorInEitherDirection() {
    assertEquals(LineSelection(3, 3, 6), LineSelection(3, 3, 3).afterLineNumberClick(rows, 6, extend = true))
    assertEquals(LineSelection(3, 2, 3), LineSelection(3, 3, 6).afterLineNumberClick(rows, 2, extend = true))
  }

  @Test
  fun shiftClickWithoutSelectionSelectsSingleLine() {
    assertEquals(LineSelection(4, 4, 4), null.afterLineNumberClick(rows, 4, extend = true))
  }

  @Test
  fun shiftClickInAnotherFileActsAsPlainClick() {
    assertEquals(LineSelection(10, 10, 10), LineSelection(3, 2, 3).afterLineNumberClick(rows, 10, extend = true))
  }

  @Test
  fun clicksOnRowsWithoutLineNumbersAreIgnored() {
    val current = LineSelection(3, 3, 3)
    for (index in listOf(0, 1, 7, 8, 12, 99)) {
      assertEquals(current, current.afterLineNumberClick(rows, index, extend = false))
      assertEquals(current, current.afterLineNumberClick(rows, index, extend = true))
    }
    assertNull(null.afterLineNumberClick(rows, 1, extend = false))
  }

  @Test
  fun containsCoversTheInclusiveRange() {
    val selection = LineSelection(4, 2, 4)
    assertFalse(1 in selection)
    assertTrue(2 in selection)
    assertTrue(4 in selection)
    assertFalse(5 in selection)
  }

  @Test
  fun copiedTextJoinsCodeWithoutMarkersOrTrailingNewline() {
    assertEquals("keep\nold\n\tnew", LineSelection(2, 2, 4).copiedText(rows))
  }

  @Test
  fun copiedTextSkipsHunkHeadersAndNoNewlineMarker() {
    assertEquals("\tnew\ntail", LineSelection(4, 4, 7).copiedText(rows))
  }

  @Test
  fun copiedTextOfSingleLine() {
    assertEquals("old", LineSelection(3, 3, 3).copiedText(rows))
  }

  @Test
  fun copiedTextIsNullWithoutLines() {
    assertNull(LineSelection(7, 7, 7).copiedText(rows))
    assertNull(LineSelection(50, 50, 50).copiedText(rows))
  }

  @Test
  fun fileIndexComesFromTheAnchor() {
    assertEquals(0, LineSelection(3, 2, 6).fileIndex(rows))
    assertEquals(1, LineSelection(10, 10, 10).fileIndex(rows))
    assertNull(LineSelection(50, 50, 50).fileIndex(rows))
  }
}

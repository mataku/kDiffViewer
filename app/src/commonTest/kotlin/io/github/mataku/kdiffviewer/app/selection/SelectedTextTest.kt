package io.github.mataku.kdiffviewer.app.selection

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectedTextTest {
  private fun selection(from: TextPosition, to: TextPosition) = DiffSelection(from, to)

  private fun single(vararg texts: String) = rowsOf(textFile("a.kt", hunk(*texts.map(::context).toTypedArray())))

  private fun copy(rows: List<io.github.mataku.kdiffviewer.app.DiffRow>, s: DiffSelection, visible: (Int) -> Boolean = { true }) = selectedText(rows, s, visible)

  @Test
  fun partialSingleRow() {
    val rows = single("hello world")
    assertEquals("world", copy(rows, selection(TextPosition(2, 6), TextPosition(2, 11))))
  }

  @Test
  fun acrossRowsJoinsWithNewline() {
    val rows = single("a", "bb", "ccc")
    assertEquals("a\nbb\ncc", copy(rows, selection(TextPosition(2, 0), TextPosition(4, 2))))
  }

  @Test
  fun endAtColumnZeroAddsTrailingNewline() {
    val rows = single("a", "bb")
    assertEquals("a\n", copy(rows, selection(TextPosition(2, 0), TextPosition(3, 0))))
  }

  @Test
  fun acrossHunksAndFilesSkipsHeaders() {
    val rows = rowsOf(
      textFile("a.kt", hunk(context("a1")), hunk(context("a2"))),
      textFile("b.kt", hunk(context("b1"))),
    )
    assertEquals("a1\na2\nb1", copy(rows, selection(TextPosition(0, 0), TextPosition(rows.lastIndex, 2))))
  }

  @Test
  fun skipsNoNewlineAtEof() {
    val rows = rowsOf(textFile("a.kt", hunk(context("a"), DiffLine(LineKind.NoNewlineAtEof, "\\ No newline", null, null), context("b"))))
    assertEquals("a\nb", copy(rows, selection(TextPosition(2, 0), TextPosition(4, 1))))
  }

  @Test
  fun skipsPlaceholders() {
    val rows = rowsOf(
      textFile("a.kt", hunk(context("a"))),
      FileDiff(null, "x.bin", ChangeType.Added, false, DiffContent.Binary),
      textFile("b.kt", hunk(context("b"))),
    )
    assertEquals("a\nb", copy(rows, selection(TextPosition(2, 0), TextPosition(rows.lastIndex, 1))))
  }

  @Test
  fun tabsPreserved() {
    val rows = single("\tx")
    assertEquals("\tx", copy(rows, selection(TextPosition(2, 0), TextPosition(2, 2))))
  }

  @Test
  fun blankLinesKept() {
    val rows = single("a", "", "b")
    assertEquals("a\n\nb", copy(rows, selection(TextPosition(2, 0), TextPosition(4, 1))))
  }

  @Test
  fun startOnHeaderRowSkipsIt() {
    val rows = single("a", "b")
    assertEquals("a\nb", copy(rows, selection(TextPosition(1, 0), TextPosition(3, 1))))
  }

  @Test
  fun invisibleRowsSkipped() {
    val rows = single("a", "b", "c")
    assertEquals("a\nc", copy(rows, selection(TextPosition(2, 0), TextPosition(4, 1)), visible = { it != 3 }))
  }

  @Test
  fun emptySelectionYieldsEmptyText() {
    val rows = single("a")
    assertEquals("", copy(rows, selection(TextPosition(2, 1), TextPosition(2, 1))))
  }
}

package io.github.mataku.kdiffviewer.core.review

import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnchorOfTest {
  @Test
  fun removedLineUsesOldSide() {
    val file = modified("a.kt", hunk(ctx("c", 6, 6), removed("x", 7)))
    assertEquals(linesAnchor("a.kt", Side.Old, 7, 7, "x"), anchorOf(file, 0, 1))
  }

  @Test
  fun addedAndContextLinesUseNewSide() {
    val file = modified("a.kt", hunk(ctx("c", 4, 5), added("y", 6)))
    assertEquals(linesAnchor("a.kt", Side.New, 5, 5, "c"), anchorOf(file, 0, 0))
    assertEquals(linesAnchor("a.kt", Side.New, 6, 6, "y"), anchorOf(file, 0, 1))
  }

  @Test
  fun contextLineWithOldSideUsesOldLineNumber() {
    val file = modified("a.kt", hunk(ctx("c", 4, 6)))
    assertEquals(linesAnchor("a.kt", Side.Old, 4, 4, "c"), anchorOf(file, 0, 0, Side.Old))
  }

  @Test
  fun contextLineWithNewSideUsesNewLineNumber() {
    val file = modified("a.kt", hunk(ctx("c", 4, 6)))
    assertEquals(linesAnchor("a.kt", Side.New, 6, 6, "c"), anchorOf(file, 0, 0, Side.New))
  }

  @Test
  fun addedLineWithOldSideIsNull() {
    val file = modified("a.kt", hunk(added("y", 6)))
    assertNull(anchorOf(file, 0, 0, Side.Old))
  }

  @Test
  fun removedLineWithNewSideIsNull() {
    val file = modified("a.kt", hunk(removed("x", 7)))
    assertNull(anchorOf(file, 0, 0, Side.New))
  }

  @Test
  fun noNewlineAtEofIsNull() {
    val file = modified("a.kt", hunk(added("y", 1), DiffLine(LineKind.NoNewlineAtEof, "\\ No newline at end of file", null, null)))
    assertNull(anchorOf(file, 0, 1))
  }

  @Test
  fun deletedFileUsesOldPath() {
    val file = deletedFile("gone.kt", hunk(removed("x", 1)))
    assertEquals("gone.kt", file.anchorPath)
    assertEquals(linesAnchor("gone.kt", Side.Old, 1, 1, "x"), anchorOf(file, 0, 0))
  }
}

package io.github.mataku.kdiffviewer.app.ui.review

import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.ViewerUiState
import io.github.mataku.kdiffviewer.app.diffLayoutOf
import io.github.mataku.kdiffviewer.app.displayRows
import io.github.mataku.kdiffviewer.app.fingerprint
import io.github.mataku.kdiffviewer.app.flattenRows
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.splitRowsOf
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.model.displayPath
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class DraftRevealTest {
  private val at = Instant.fromEpochSeconds(0)
  private val onLine = Placement.OnLine(LineLocation(1, 0, 1))
  private val placed = ReviewThread("t1", Anchor.File("a.kt"), listOf(Comment("c1", Author.User, "x", at)), false, false)
  private val unplaced = ReviewThread("t2", Anchor.File("b.kt"), listOf(Comment("c2", Author.User, "y", at)), false, false)
  private val threads = listOf(placed, unplaced)
  private val placementById = mapOf("t1" to onLine)

  @Test
  fun replyAndEditUseTheThreadPlacement() {
    assertEquals(onLine, draftPlacement(DraftTarget.Reply("t1"), threads, placementById))
    assertEquals(onLine, draftPlacement(DraftTarget.Edit("c1"), threads, placementById))
  }

  @Test
  fun unplacedThreadIsOrphanAndUnknownThreadIsNull() {
    assertEquals(Placement.Orphan, draftPlacement(DraftTarget.Reply("t2"), threads, placementById))
    assertNull(draftPlacement(DraftTarget.Reply("t9"), threads, placementById))
    assertNull(draftPlacement(DraftTarget.Edit("c9"), threads, placementById))
  }

  private fun file(path: String) = FileDiff(
    path,
    path,
    ChangeType.Modified,
    false,
    DiffContent.Text(listOf(Hunk(1, 2, 1, 2, "", listOf(DiffLine(LineKind.Context, "a", 1, 1), DiffLine(LineKind.Context, "b", 2, 2))))),
  )

  private fun stateOf(files: List<FileDiff>): ViewerUiState {
    val rows = flattenRows(files)
    val headers = rows.mapIndexedNotNull { i, r -> if (r is DiffRow.FileHeader) i else null }
    return ViewerUiState(files = files, rows = rows, fileHeaderRowIndex = headers, fingerprints = files.map { it.fingerprint() })
  }

  @Test
  fun lineDraftRevealsItsLineRowAndFileDraftItsHeader() {
    val ui = stateOf(listOf(file("a.kt"), file("b.kt")))
    val layout = diffLayoutOf(ui)
    val display = ui.displayRows(DiffMode.Unified)
    assertEquals(layout.visibleIndexOf(ui.fileHeaderRowIndex[1] + 3), revealIndex(onLine, layout, ui.rows, display, null))
    assertEquals(layout.headerVisibleIndex(1), revealIndex(Placement.OnFile(1), layout, ui.rows, display, null))
    assertEquals(0, revealIndex(Placement.Orphan, layout, ui.rows, display, null))
  }

  @Test
  fun lineDraftInCollapsedFileRevealsTheHeader() {
    val base = stateOf(listOf(file("a.kt"), file("b.kt")))
    val ui = base.copy(collapsed = setOf(CollapsedFile(base.files[1].displayPath, base.fingerprints[1])))
    val layout = diffLayoutOf(ui)
    val display = ui.displayRows(DiffMode.Unified)
    assertEquals(layout.headerVisibleIndex(1), revealIndex(onLine, layout, ui.rows, display, null))
  }

  @Test
  fun splitRevealsOldContextAnchor() {
    val base = stateOf(listOf(file("a.kt"), file("b.kt")))
    val ui = base.copy(split = splitRowsOf(base.rows))
    val layout = diffLayoutOf(ui, DiffMode.Split)
    val display = ui.displayRows(DiffMode.Split)
    val contextRow = ui.fileHeaderRowIndex[1] + 3
    assertEquals(layout.visibleIndexOf(ui.split.splitIndexOf(contextRow)), revealIndex(onLine, layout, ui.rows, display, Side.Old))
  }
}

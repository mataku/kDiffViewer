package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.runtime.State
import io.github.mataku.kdiffviewer.app.DiffLayout
import io.github.mataku.kdiffviewer.app.DiffMode
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.DisplayRows
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.toSide
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread

internal class RevealingReviewActions(
  private val delegate: ReviewActions,
  private val draftText: State<String>,
  private val reveal: () -> Unit,
) : ReviewActions by delegate {
  override fun startDraft(target: DraftTarget): Boolean {
    val started = delegate.startDraft(target)
    if (!started && draftText.value.isNotBlank()) reveal()
    return started
  }
}

internal fun draftPlacement(target: DraftTarget, threads: List<ReviewThread>, placementById: Map<String, Placement>): Placement? {
  val threadId = when (target) {
    is DraftTarget.NewThread -> return target.placement
    is DraftTarget.Reply -> threads.firstOrNull { it.id == target.threadId }?.id
    is DraftTarget.Edit -> threads.firstOrNull { thread -> thread.comments.any { it.id == target.commentId } }?.id
  } ?: return null
  return placementById[threadId] ?: Placement.Orphan
}

internal fun Placement.fileIndexOrNull(): Int? = when (this) {
  is Placement.OnLine -> location.fileIndex
  is Placement.OnFile -> fileIndex
  Placement.Orphan -> null
}

internal fun draftAnchor(target: DraftTarget, threads: List<ReviewThread>): Anchor? = when (target) {
  is DraftTarget.NewThread -> target.anchor
  is DraftTarget.Reply -> threads.firstOrNull { it.id == target.threadId }?.anchor
  is DraftTarget.Edit -> threads.firstOrNull { thread -> thread.comments.any { it.id == target.commentId } }?.anchor
}

internal fun revealIndex(placement: Placement, anchor: Anchor?, layout: DiffLayout, rows: List<DiffRow>, display: DisplayRows): Int {
  val fileIndex = placement.fileIndexOrNull()
  if (fileIndex == null || fileIndex >= layout.fileCount) return 0
  val header = layout.headerRowIndex(fileIndex)
  val lines = anchor as? Anchor.Lines
  if (placement !is Placement.OnLine || lines == null) return layout.headerVisibleIndex(fileIndex)
  val side = lines.side.toSide()
  fun matches(unifiedRow: Int): Boolean {
    val line = (rows.getOrNull(unifiedRow) as? DiffRow.Line)?.line ?: return false
    if (line.kind == LineKind.NoNewlineAtEof) return false
    return (if (side == Side.Old) line.oldLineNo else line.newLineNo) == lines.endLine
  }
  for (offset in 1..layout.bodyRowCount(fileIndex)) {
    val displayRow = header + offset
    val hit = if (display.mode == DiffMode.Unified) matches(displayRow) else matches(display.unifiedOf(displayRow, side))
    if (hit) return layout.visibleIndexOf(displayRow)
  }
  return layout.headerVisibleIndex(fileIndex)
}

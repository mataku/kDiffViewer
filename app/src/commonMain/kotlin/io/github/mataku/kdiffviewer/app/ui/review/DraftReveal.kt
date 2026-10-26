package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.runtime.State
import io.github.mataku.kdiffviewer.app.DiffLayout
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.app.review.DraftTarget
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

internal fun revealIndex(placement: Placement, layout: DiffLayout, rows: List<DiffRow>): Int {
  val fileIndex = placement.fileIndexOrNull()
  if (fileIndex == null || fileIndex >= layout.fileCount) return 0
  val header = layout.headerRowIndex(fileIndex)
  if (placement !is Placement.OnLine) return layout.headerVisibleIndex(fileIndex)
  val location = placement.location
  for (offset in 1..layout.bodyRowCount(fileIndex)) {
    val row = rows[header + offset]
    if (row is DiffRow.Line && row.hunkIndex == location.hunkIndex && row.lineIndex == location.lineIndex) {
      return layout.visibleIndexOf(header + offset)
    }
  }
  return layout.headerVisibleIndex(fileIndex)
}

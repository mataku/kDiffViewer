package io.github.mataku.kdiffviewer.app.review

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.Side

@Immutable
class RenderedNotes(val threads: Map<Int, List<ReviewThread>>, val draftFile: Int) {
  val files: Set<Int>
    get() = if (draftFile < 0) threads.keys else threads.keys + draftFile

  companion object {
    val Empty = RenderedNotes(emptyMap(), -1)
  }
}

private class RowSpan(val fileIndex: Int, val side: Side, val lines: IntRange)

private fun DiffRow.span(): RowSpan? = when (this) {
  is DiffRow.RenderedBlock -> RowSpan(fileIndex, Side.New, source.startLine..source.endLine)
  is DiffRow.RemovedMarker -> RowSpan(fileIndex, Side.Old, oldStart..oldEnd)
  else -> null
}

private fun ReviewPlacements.threadsOn(span: RowSpan): List<ReviewThread> {
  var threads: List<ReviewThread>? = null
  for (line in span.lines) {
    val found = byLine[sidePosition(span.fileIndex, span.side, line)] ?: continue
    threads = threads?.plus(found) ?: found
  }
  return threads.orEmpty()
}

fun ReviewPlacements.threadsOn(row: DiffRow.RenderedBlock): List<ReviewThread> = threadsOn(row.span()!!)

fun ReviewPlacements.threadsOn(row: DiffRow.RemovedMarker): List<ReviewThread> = threadsOn(row.span()!!)

private fun DraftTarget?.lineDraft(): Pair<Anchor.Lines, Int>? {
  val target = this as? DraftTarget.NewThread ?: return null
  val anchor = target.anchor as? Anchor.Lines ?: return null
  val placement = target.placement as? Placement.OnLine ?: return null
  return anchor to placement.location.fileIndex
}

fun DraftTarget?.composesOn(row: DiffRow): Boolean {
  val (anchor, fileIndex) = lineDraft() ?: return false
  val span = row.span() ?: return false
  return span.fileIndex == fileIndex && span.side == anchor.side && anchor.endLine in span.lines
}

private fun List<RowSpan>.covers(line: Int): Boolean = binarySearch { span ->
  when {
    line < span.lines.first -> 1
    line > span.lines.last -> -1
    else -> 0
  }
} >= 0

fun renderedNotes(rows: List<DiffRow>, headers: List<Int>, renderedFiles: Set<Int>, placements: ReviewPlacements, draft: DraftTarget?): RenderedNotes {
  val draftLine = draft.lineDraft()
  val threadsByFile = placements.byLine.entries.groupBy({ (it.key shr 40).toInt() }, { it })
  if (renderedFiles.none { it in threadsByFile || it == draftLine?.second }) return RenderedNotes.Empty
  val threads = HashMap<Int, List<ReviewThread>>()
  var draftFile = -1
  for (fileIndex in renderedFiles) {
    val entries = threadsByFile[fileIndex].orEmpty()
    val composing = draftLine?.second == fileIndex
    if (entries.isEmpty() && !composing) continue
    val start = headers[fileIndex] + 1
    val end = headers.getOrNull(fileIndex + 1) ?: rows.size
    val (oldSpans, newSpans) = rows.subList(start, end).mapNotNull { it.span() }.partition { it.side == Side.Old }
    fun covered(side: Side, line: Int) = (if (side == Side.Old) oldSpans else newSpans).covers(line)
    val unplaced = entries.flatMap { it.value }.filter { thread ->
      val anchor = thread.anchor as? Anchor.Lines
      anchor != null && !covered(anchor.side, anchor.endLine)
    }
    if (unplaced.isNotEmpty()) threads[fileIndex] = unplaced
    if (composing && !covered(draftLine.first.side, draftLine.first.endLine)) draftFile = fileIndex
  }
  return if (threads.isEmpty() && draftFile < 0) RenderedNotes.Empty else RenderedNotes(threads, draftFile)
}

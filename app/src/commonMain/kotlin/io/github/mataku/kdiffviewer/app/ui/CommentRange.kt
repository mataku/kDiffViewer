package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.toSide
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.locate
import kotlin.math.max
import kotlin.math.min
import io.github.mataku.kdiffviewer.core.review.Side as ReviewSide

data class CommentRange(
  val fileIndex: Int,
  val hunkIndex: Int,
  val side: Side?,
  val fromLineIndex: Int,
  val toLineIndex: Int,
) {
  fun contains(fileIndex: Int, hunkIndex: Int, lineIndex: Int, side: Side?): Boolean = this.fileIndex == fileIndex &&
    this.hunkIndex == hunkIndex &&
    this.side == side &&
    lineIndex in min(fromLineIndex, toLineIndex)..max(fromLineIndex, toLineIndex)
}

@Stable
class CommentRangeState {
  private var draggingState by mutableStateOf<CommentRange?>(null)
  private var draftedState by mutableStateOf<CommentRange?>(null)

  var dragging: CommentRange?
    get() = draggingState
    set(value) {
      if (draggingState != value) draggingState = value
    }

  var drafted: CommentRange?
    get() = draftedState
    set(value) {
      if (draftedState != value) draftedState = value
    }

  fun highlights(fileIndex: Int, hunkIndex: Int, lineIndex: Int, side: Side?): Boolean = (dragging ?: drafted)?.contains(fileIndex, hunkIndex, lineIndex, side) == true

  fun highlights(position: Long, side: Side?): Boolean {
    if (position < 0) return false
    return highlights((position shr 40).toInt(), ((position shr 20) and 0xFFFFF).toInt(), (position and 0xFFFFF).toInt(), side)
  }
}

fun draftRange(target: DraftTarget?, files: List<FileDiff>, split: Boolean): CommentRange? {
  val thread = target as? DraftTarget.NewThread ?: return null
  val anchor = thread.anchor as? Anchor.Lines ?: return null
  if (anchor.endLine <= anchor.startLine) return null
  val location = (thread.placement as? Placement.OnLine)?.location ?: return null
  val file = files.getOrNull(location.fileIndex) ?: return null
  val last = file.locate(anchor.side, anchor.endLine) ?: return null
  val lines = (file.content as? DiffContent.Text)?.hunks?.getOrNull(last.hunkIndex)?.lines ?: return null
  var index = last.lineIndex
  while (index >= 0) {
    val lineNo = lines[index].numberOn(anchor.side)
    if (lineNo != null && lineNo <= anchor.startLine) break
    index--
  }
  if (index < 0) return null
  return CommentRange(
    location.fileIndex,
    last.hunkIndex,
    if (split) anchor.side.toSide() else null,
    index,
    last.lineIndex,
  )
}

private fun DiffLine.numberOn(side: ReviewSide): Int? {
  if (kind == LineKind.NoNewlineAtEof) return null
  return if (side == ReviewSide.Old) oldLineNo else newLineNo
}

package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.model.displayPath

enum class AnchorEdge { None, Orphans, End }

data class ScrollAnchor(val displayPath: String, val rowOffset: Int, val edge: AnchorEdge = AnchorEdge.None)

fun scrollAnchorOf(state: ViewerUiState, layout: DiffLayout, display: DisplayRows, firstVisibleIndex: Int): ScrollAnchor? {
  if (layout.fileCount == 0 || layout.visibleCount == 0) return null
  val visibleIndex = firstVisibleIndex.coerceIn(0, layout.visibleCount - 1)
  val fileIndex = layout.fileIndexOfVisible(visibleIndex)
  val path = state.files[fileIndex].displayPath
  if (layout.hasOrphanItem && visibleIndex == 0) return ScrollAnchor(path, 0, AnchorEdge.Orphans)
  if (layout.hasEndItem && visibleIndex == layout.visibleCount - 1) return ScrollAnchor(path, 0, AnchorEdge.End)
  val displayRow = layout.rowIndexOf(visibleIndex)
  val unifiedRow = display.unifiedOf(displayRow, Side.Old).takeIf { it >= 0 } ?: display.unifiedOf(displayRow, Side.New)
  return ScrollAnchor(path, unifiedRow - state.fileHeaderRowIndex[fileIndex])
}

fun restoreTargetOf(anchor: ScrollAnchor, state: ViewerUiState, layout: DiffLayout, display: DisplayRows): Int? {
  when (anchor.edge) {
    AnchorEdge.Orphans -> return if (layout.hasOrphanItem) 0 else null
    AnchorEdge.End -> return if (layout.hasEndItem) layout.visibleCount - 1 else null
    AnchorEdge.None -> Unit
  }
  val fileIndex = state.files.indexOfFirst { it.displayPath == anchor.displayPath }
  if (fileIndex < 0 || fileIndex >= layout.fileCount) return null
  val header = state.fileHeaderRowIndex[fileIndex]
  val end = if (fileIndex + 1 < state.fileHeaderRowIndex.size) state.fileHeaderRowIndex[fileIndex + 1] else state.rows.size
  return layout.visibleIndexOf(display.displayOf(header + minOf(anchor.rowOffset, end - header - 1)))
}

class ScrollRestorer {
  private var loadId: Long? = null
  private var repo: RepoPath? = null
  private var baseSpec: BaseSpec? = null
  private var lastAnchor: ScrollAnchor? = null
  private var pending: ScrollAnchor? = null
  private var pendingTop = false
  private var mode: DiffMode? = null
  private var restoredTarget: Int? = null

  fun next(state: ViewerUiState, layout: DiffLayout, display: DisplayRows, firstVisibleIndex: Int): Int? {
    if (state.loadId != loadId) {
      val sameRepo = repo != null && state.repo == repo
      val baseChanged = sameRepo && state.baseSpec != baseSpec
      pendingTop = baseChanged
      pending = lastAnchor.takeIf { sameRepo && !baseChanged }
      lastAnchor = null
      restoredTarget = null
      loadId = state.loadId
      repo = state.repo
      baseSpec = state.baseSpec
    } else if (mode != null && mode != display.mode) {
      pending = pending ?: lastAnchor
    }
    mode = display.mode
    if (pendingTop) {
      if (layout.visibleCount > 0) {
        pendingTop = false
        return 0
      }
      if (state.status != LoadStatus.Loading) pendingTop = false
      return null
    }
    val anchor = pending
    if (anchor != null) {
      val target = restoreTargetOf(anchor, state, layout, display)
      if (target != null) {
        lastAnchor = anchor
        restoredTarget = target
      }
      if (target != null || state.status != LoadStatus.Loading) pending = null
      return target
    }
    if (restoredTarget == firstVisibleIndex) return null
    restoredTarget = null
    lastAnchor = scrollAnchorOf(state, layout, display, firstVisibleIndex)
    return null
  }
}

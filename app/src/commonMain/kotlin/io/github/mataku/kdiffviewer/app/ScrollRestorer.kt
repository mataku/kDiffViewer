package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.displayPath

data class ScrollAnchor(val displayPath: String, val rowOffset: Int)

fun scrollAnchorOf(state: ViewerUiState, layout: DiffLayout, firstVisibleIndex: Int): ScrollAnchor? {
  if (layout.fileCount == 0 || layout.visibleCount == 0) return null
  val visibleIndex = firstVisibleIndex.coerceIn(0, layout.visibleCount - 1)
  val fileIndex = layout.fileIndexOfVisible(visibleIndex)
  return ScrollAnchor(state.files[fileIndex].displayPath, layout.rowIndexOf(visibleIndex) - layout.headerRowIndex(fileIndex))
}

fun restoreTargetOf(anchor: ScrollAnchor, state: ViewerUiState, layout: DiffLayout): Int? {
  val fileIndex = state.files.indexOfFirst { it.displayPath == anchor.displayPath }
  if (fileIndex < 0 || fileIndex >= layout.fileCount) return null
  val header = layout.headerRowIndex(fileIndex)
  return layout.visibleIndexOf(header + minOf(anchor.rowOffset, layout.bodyRowCount(fileIndex)))
}

class ScrollRestorer {
  private var loadId: Long? = null
  private var repo: RepoPath? = null
  private var baseSpec: BaseSpec? = null
  private var lastAnchor: ScrollAnchor? = null
  private var pending: ScrollAnchor? = null
  private var pendingTop = false

  fun next(state: ViewerUiState, layout: DiffLayout, firstVisibleIndex: Int): Int? {
    if (state.loadId != loadId) {
      val sameRepo = repo != null && state.repo == repo
      val baseChanged = sameRepo && state.baseSpec != baseSpec
      pendingTop = baseChanged
      pending = lastAnchor.takeIf { sameRepo && !baseChanged }
      lastAnchor = null
      loadId = state.loadId
      repo = state.repo
      baseSpec = state.baseSpec
    }
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
      val target = restoreTargetOf(anchor, state, layout)
      if (target != null || state.status != LoadStatus.Loading) pending = null
      return target
    }
    lastAnchor = scrollAnchorOf(state, layout, firstVisibleIndex)
    return null
  }
}

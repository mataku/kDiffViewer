package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import io.github.mataku.kdiffviewer.app.DiffLayout

data class FileFrame(val top: Float, val bottom: Float, val roundTop: Boolean, val roundBottom: Boolean)

fun fileFrames(layout: DiffLayout, items: List<LazyListItemInfo>, headerInset: Float, viewportHeight: Float, overshoot: Float): List<FileFrame> {
  val frames = ArrayList<FileFrame>()
  var file = -1
  var firstIndex = 0
  var firstOffset = 0
  var lastIndex = 0
  var lastEnd = 0
  fun emit() {
    if (file < 0) return
    val roundTop = firstIndex == layout.headerVisibleIndex(file)
    val roundBottom = lastIndex == layout.lastVisibleIndex(file)
    frames.add(
      FileFrame(
        top = if (roundTop) firstOffset + headerInset else -overshoot,
        bottom = if (roundBottom) lastEnd.toFloat() else viewportHeight + overshoot,
        roundTop = roundTop,
        roundBottom = roundBottom,
      ),
    )
  }
  for (item in items) {
    val index = item.index
    if (index < 0 || index >= layout.visibleCount) continue
    if (layout.hasOrphanItem && index == 0) continue
    if (layout.hasEndItem && index == layout.visibleCount - 1) continue
    val itemFile = layout.fileIndexOfVisible(index)
    if (itemFile != file) {
      emit()
      file = itemFile
      firstIndex = index
      firstOffset = item.offset
    }
    lastIndex = index
    lastEnd = item.offset + item.size
  }
  emit()
  return frames
}

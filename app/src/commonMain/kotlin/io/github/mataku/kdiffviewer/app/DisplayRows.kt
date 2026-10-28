package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.core.model.Side

enum class DiffMode { Unified, Split }

@Immutable
class DisplayRows(
  val mode: DiffMode,
  private val split: SplitRows,
  private val unifiedSize: Int,
) {
  fun unifiedOf(displayRow: Int, side: Side? = null): Int {
    if (mode == DiffMode.Unified) return displayRow
    if (displayRow == split.size) return unifiedSize
    return when (side) {
      Side.Old -> split.leftOf(displayRow)
      Side.New -> split.rightOf(displayRow)
      null -> split.rightOf(displayRow).takeIf { it != -1 } ?: split.leftOf(displayRow)
    }
  }

  fun displayOf(unifiedRow: Int): Int {
    if (mode == DiffMode.Unified) return unifiedRow
    return if (unifiedRow == unifiedSize) split.size else split.splitIndexOf(unifiedRow)
  }
}

fun ViewerUiState.displayRows(mode: DiffMode): DisplayRows = DisplayRows(mode, split, rows.size)

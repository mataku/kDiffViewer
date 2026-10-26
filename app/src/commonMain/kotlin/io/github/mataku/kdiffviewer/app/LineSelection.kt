package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.core.model.LineKind

@Immutable
data class LineSelection(val anchor: Int, val start: Int, val end: Int) {
  operator fun contains(rowIndex: Int): Boolean = rowIndex in start..end

  fun fileIndex(rows: List<DiffRow>): Int? = (rows.getOrNull(anchor) as? DiffRow.Line)?.fileIndex

  fun copiedText(rows: List<DiffRow>): String? {
    val builder = StringBuilder()
    var first = true
    for (index in start..minOf(end, rows.lastIndex)) {
      val row = rows[index] as? DiffRow.Line ?: continue
      if (row.line.kind == LineKind.NoNewlineAtEof) continue
      if (!first) builder.append('\n')
      builder.append(row.line.text)
      first = false
    }
    return if (first) null else builder.toString()
  }
}

fun LineSelection?.afterLineNumberClick(rows: List<DiffRow>, rowIndex: Int, extend: Boolean): LineSelection? {
  val row = rows.getOrNull(rowIndex) as? DiffRow.Line ?: return this
  if (row.line.kind == LineKind.NoNewlineAtEof) return this
  if (this != null && extend && fileIndex(rows) == row.fileIndex) {
    return LineSelection(anchor, minOf(anchor, rowIndex), maxOf(anchor, rowIndex))
  }
  if (this != null && start == rowIndex && end == rowIndex) return null
  return LineSelection(rowIndex, rowIndex, rowIndex)
}

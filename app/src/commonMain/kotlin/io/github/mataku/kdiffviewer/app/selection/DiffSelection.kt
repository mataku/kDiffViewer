package io.github.mataku.kdiffviewer.app.selection

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.Side

data class TextPosition(val row: Int, val column: Int) : Comparable<TextPosition> {
  override fun compareTo(other: TextPosition): Int = if (row != other.row) row.compareTo(other.row) else column.compareTo(other.column)
}

@Immutable
data class DiffSelection(val anchor: TextPosition, val focus: TextPosition, val side: Side? = null) {
  val start: TextPosition get() = if (anchor <= focus) anchor else focus
  val end: TextPosition get() = if (anchor <= focus) focus else anchor
  val isEmpty: Boolean get() = anchor == focus

  fun rangeFor(row: Int, length: Int): IntRange? {
    val start = start
    val end = end
    if (row < start.row || row > end.row) return null
    val from = if (row == start.row) start.column.coerceIn(0, length) else 0
    val to = if (row == end.row) end.column.coerceIn(0, length) else length
    return if (from < to) from until to else null
  }
}

fun List<DiffRow>.selectableRow(row: Int, side: Side? = null): Boolean {
  val candidate = getOrNull(row)
  if (candidate !is DiffRow.Line) return false
  return when (candidate.line.kind) {
    LineKind.NoNewlineAtEof -> false
    LineKind.Added -> side != Side.Old
    LineKind.Removed -> side != Side.New
    else -> true
  }
}

fun List<DiffRow>.positionAtOrAfter(row: Int, side: Side? = null): TextPosition? {
  for (index in row.coerceAtLeast(0) until size) {
    if (selectableRow(index, side)) return TextPosition(index, 0)
  }
  for (index in minOf(row, size) - 1 downTo 0) {
    if (selectableRow(index, side)) return TextPosition(index, (get(index) as DiffRow.Line).line.text.length)
  }
  return null
}

fun List<DiffRow>.nextSelectableRow(row: Int, side: Side? = null): Int? {
  for (index in (row + 1).coerceAtLeast(0) until size) {
    if (selectableRow(index, side)) return index
  }
  return null
}

fun selectedText(rows: List<DiffRow>, selection: DiffSelection, isVisible: (row: Int) -> Boolean): String {
  if (selection.isEmpty) return ""
  val start = selection.start
  val end = selection.end
  val builder = StringBuilder()
  var contributed = false
  for (index in start.row.coerceAtLeast(0)..minOf(end.row, rows.lastIndex)) {
    if (!rows.selectableRow(index, selection.side) || !isVisible(index)) continue
    val text = (rows[index] as DiffRow.Line).line.text
    val from = if (index == start.row) start.column.coerceIn(0, text.length) else 0
    val to = if (index == end.row) end.column.coerceIn(0, text.length) else text.length
    if (index == end.row && index > start.row && end.column == 0) continue
    if (contributed) builder.append('\n')
    builder.append(text, from, maxOf(from, to))
    contributed = true
  }
  if (contributed && end.column == 0 && end.row > start.row) builder.append('\n')
  return builder.toString()
}

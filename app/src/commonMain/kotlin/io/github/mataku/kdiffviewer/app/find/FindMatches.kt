package io.github.mataku.kdiffviewer.app.find

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.app.CollapsedFile
import io.github.mataku.kdiffviewer.app.DiffRow
import io.github.mataku.kdiffviewer.core.model.LineKind

const val FIND_MATCH_LIMIT = 10_000
const val FIND_QUERY_LIMIT = 1_000

@Immutable
data class FindKey(
  val query: String,
  val loadId: Long,
  val rowsGeneration: Long,
  val collapsed: Set<CollapsedFile> = emptySet(),
  val expanded: Set<CollapsedFile> = emptySet(),
)

@Immutable
class FindMatches private constructor(
  val key: FindKey?,
  val scannedRows: Int,
  private val rows: IntArray,
  private val starts: IntArray,
  val count: Int,
  val capped: Boolean,
) {
  val queryLength: Int get() = key?.query?.length ?: 0

  fun rowAt(index: Int): Int = rows[index]

  fun startAt(index: Int): Int = starts[index]

  fun firstIndexAtOrAfter(row: Int): Int {
    var low = 0
    var high = count
    while (low < high) {
      val mid = (low + high) ushr 1
      if (rows[mid] < row) low = mid + 1 else high = mid
    }
    return low
  }

  fun firstIndexOfRow(row: Int): Int {
    val index = firstIndexAtOrAfter(row)
    return if (index < count && rows[index] == row) index else -1
  }

  fun isFor(loadId: Long, rowsGeneration: Long): Boolean = key != null && key.loadId == loadId && key.rowsGeneration == rowsGeneration

  fun updatedFor(rows: List<DiffRow>, key: FindKey, limit: Int = FIND_MATCH_LIMIT, skipFile: (fileIndex: Int) -> Boolean = { false }): FindMatches {
    if (key.query.isEmpty()) return FindMatches(key, rows.size, EMPTY, EMPTY, 0, false)
    val appending = key == this.key && rows.size >= scannedRows
    if (appending && rows.size == scannedRows) return this
    if (appending && capped) return FindMatches(key, rows.size, this.rows, starts, count, true)
    val builder = Builder(if (appending) count + 16 else 16)
    val from = if (appending) {
      for (index in 0 until count) builder.add(this.rows[index], starts[index])
      scannedRows
    } else {
      0
    }
    val capped = builder.scan(rows, key.query, from, limit, skipFile)
    return builder.build(key, rows.size, capped)
  }

  private class Builder(capacity: Int) {
    private var rows = IntArray(capacity.coerceAtLeast(1))
    private var starts = IntArray(rows.size)
    private var size = 0

    fun add(row: Int, start: Int) {
      if (size == rows.size) {
        val grown = rows.size * 2
        rows = rows.copyOf(grown)
        starts = starts.copyOf(grown)
      }
      rows[size] = row
      starts[size] = start
      size++
    }

    fun scan(source: List<DiffRow>, query: String, from: Int, limit: Int, skipFile: (fileIndex: Int) -> Boolean): Boolean {
      val length = query.length
      for (rowIndex in from until source.size) {
        val row = source[rowIndex] as? DiffRow.Line ?: continue
        if (row.line.kind == LineKind.NoNewlineAtEof || skipFile(row.fileIndex)) continue
        val text = row.line.text
        var start = 0
        val last = text.length - length
        while (start <= last) {
          if (text.regionMatches(start, query, 0, length, ignoreCase = true)) {
            if (size >= limit) return true
            add(rowIndex, start)
            start += length
          } else {
            start++
          }
        }
      }
      return false
    }

    fun build(key: FindKey?, scannedRows: Int, capped: Boolean): FindMatches = FindMatches(key, scannedRows, rows, starts, size, capped)
  }

  companion object {
    private val EMPTY = IntArray(0)
    val Empty = FindMatches(null, 0, EMPTY, EMPTY, 0, false)
  }
}

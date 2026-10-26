package io.github.mataku.kdiffviewer.app

class DiffLayout internal constructor(
  private val headerRows: IntArray,
  private val headerVisible: IntArray,
  private val collapsed: BooleanArray,
  private val notes: BooleanArray,
  private val rowCount: Int,
  val visibleCount: Int,
  val hasOrphanItem: Boolean = false,
) {
  val fileCount: Int get() = headerRows.size

  fun headerRowIndex(fileIndex: Int): Int = headerRows[fileIndex]

  fun headerVisibleIndex(fileIndex: Int): Int = headerVisible[fileIndex]

  fun isCollapsed(fileIndex: Int): Boolean = collapsed[fileIndex]

  fun hasNotes(fileIndex: Int): Boolean = notes[fileIndex]

  fun bodyRowCount(fileIndex: Int): Int {
    val end = if (fileIndex + 1 < headerRows.size) headerRows[fileIndex + 1] else rowCount
    return end - headerRows[fileIndex] - 1
  }

  fun fileIndexOfVisible(visibleIndex: Int): Int = if (headerRows.isEmpty()) -1 else lastAtOrBelow(headerVisible, visibleIndex)

  fun rowIndexOf(visibleIndex: Int): Int {
    if (headerRows.isEmpty()) return -1
    if (hasOrphanItem && visibleIndex == 0) return headerRows[0]
    val fileIndex = fileIndexOfVisible(visibleIndex)
    val offset = visibleIndex - headerVisible[fileIndex]
    if (!notes[fileIndex]) return headerRows[fileIndex] + offset
    return headerRows[fileIndex] + (offset - 1).coerceAtLeast(0)
  }

  fun visibleIndexOf(rowIndex: Int): Int {
    if (headerRows.isEmpty()) return -1
    val fileIndex = lastAtOrBelow(headerRows, rowIndex)
    if (collapsed[fileIndex]) return headerVisible[fileIndex]
    val offset = rowIndex - headerRows[fileIndex]
    return headerVisible[fileIndex] + offset + if (offset > 0 && notes[fileIndex]) 1 else 0
  }

  private fun lastAtOrBelow(sorted: IntArray, value: Int): Int {
    val search = sorted.binarySearch(value)
    return if (search >= 0) search else (-search - 2).coerceAtLeast(0)
  }
}

fun diffLayoutOf(state: ViewerUiState, notesFiles: Set<Int> = emptySet(), hasOrphans: Boolean = false): DiffLayout {
  val headerRows = state.fileHeaderRowIndex
  val fileCount = headerRows.size
  val rowCount = state.rows.size
  val headerRowArray = IntArray(fileCount)
  val headerVisible = IntArray(fileCount)
  val collapsed = BooleanArray(fileCount)
  val notes = BooleanArray(fileCount)
  var visible = if (hasOrphans) 1 else 0
  for (fileIndex in 0 until fileCount) {
    val header = headerRows[fileIndex]
    val end = if (fileIndex + 1 < fileCount) headerRows[fileIndex + 1] else rowCount
    headerRowArray[fileIndex] = header
    headerVisible[fileIndex] = visible
    collapsed[fileIndex] = state.isCollapsed(fileIndex)
    notes[fileIndex] = !collapsed[fileIndex] && fileIndex in notesFiles
    visible += if (collapsed[fileIndex]) 1 else end - header + if (notes[fileIndex]) 1 else 0
  }
  return DiffLayout(headerRowArray, headerVisible, collapsed, notes, rowCount, visible, hasOrphans)
}

package io.github.mataku.kdiffviewer.app

class DiffLayout internal constructor(
  private val headerRows: IntArray,
  private val headerVisible: IntArray,
  private val collapsed: BooleanArray,
  private val rowCount: Int,
  val visibleCount: Int,
) {
  val fileCount: Int get() = headerRows.size

  fun headerRowIndex(fileIndex: Int): Int = headerRows[fileIndex]

  fun headerVisibleIndex(fileIndex: Int): Int = headerVisible[fileIndex]

  fun isCollapsed(fileIndex: Int): Boolean = collapsed[fileIndex]

  fun bodyRowCount(fileIndex: Int): Int {
    val end = if (fileIndex + 1 < headerRows.size) headerRows[fileIndex + 1] else rowCount
    return end - headerRows[fileIndex] - 1
  }

  fun fileIndexOfVisible(visibleIndex: Int): Int = lastAtOrBelow(headerVisible, visibleIndex)

  fun rowIndexOf(visibleIndex: Int): Int {
    val fileIndex = fileIndexOfVisible(visibleIndex)
    return headerRows[fileIndex] + (visibleIndex - headerVisible[fileIndex])
  }

  fun visibleIndexOf(rowIndex: Int): Int {
    val fileIndex = lastAtOrBelow(headerRows, rowIndex)
    if (collapsed[fileIndex]) return headerVisible[fileIndex]
    return headerVisible[fileIndex] + (rowIndex - headerRows[fileIndex])
  }

  private fun lastAtOrBelow(sorted: IntArray, value: Int): Int {
    val search = sorted.binarySearch(value)
    return if (search >= 0) search else (-search - 2).coerceAtLeast(0)
  }
}

fun diffLayoutOf(state: ViewerUiState): DiffLayout {
  val headerRows = state.fileHeaderRowIndex
  val fileCount = headerRows.size
  val rowCount = state.rows.size
  val headerRowArray = IntArray(fileCount)
  val headerVisible = IntArray(fileCount)
  val collapsed = BooleanArray(fileCount)
  var visible = 0
  for (fileIndex in 0 until fileCount) {
    val header = headerRows[fileIndex]
    val end = if (fileIndex + 1 < fileCount) headerRows[fileIndex + 1] else rowCount
    headerRowArray[fileIndex] = header
    headerVisible[fileIndex] = visible
    collapsed[fileIndex] = state.isCollapsed(fileIndex)
    visible += if (collapsed[fileIndex]) 1 else end - header
  }
  return DiffLayout(headerRowArray, headerVisible, collapsed, rowCount, visible)
}

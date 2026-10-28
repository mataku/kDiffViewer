package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.core.model.LineKind

@Immutable
class SplitRows internal constructor(
  private val left: IntSnapshot,
  private val right: IntSnapshot,
  private val splitIndex: IntSnapshot,
) {
  val size: Int get() = left.size

  fun leftOf(splitRow: Int): Int = left[splitRow]

  fun rightOf(splitRow: Int): Int = right[splitRow]

  fun splitIndexOf(unifiedRow: Int): Int = splitIndex[unifiedRow]

  companion object {
    val Empty: SplitRows = SplitRowsBuilder().snapshot()
  }
}

class SplitRowsBuilder {
  private val left = IntAppendBuffer()
  private val right = IntAppendBuffer()
  private val splitIndex = IntAppendBuffer()

  fun append(rows: List<DiffRow>, from: Int) {
    require(from == splitIndex.size) { "from: $from, expected: ${splitIndex.size}" }
    var removedStart = 0
    var removedEnd = 0
    var addedStart = 0
    var addedEnd = 0

    fun flush() {
      val removedCount = removedEnd - removedStart
      val addedCount = addedEnd - addedStart
      for (i in 0 until maxOf(removedCount, addedCount)) {
        emit(
          if (i < removedCount) removedStart + i else -1,
          if (i < addedCount) addedStart + i else -1,
        )
      }
      removedEnd = removedStart
      addedEnd = addedStart
    }

    for (index in from until rows.size) {
      splitIndex.add(-1)
      val row = rows[index]
      val kind = (row as? DiffRow.Line)?.line?.kind
      val inAddedRun = addedEnd > addedStart
      val inRemovedRun = removedEnd > removedStart
      when {
        kind == LineKind.Removed -> {
          if (inAddedRun) {
            flush()
            removedStart = index
          } else if (!inRemovedRun) {
            removedStart = index
          }
          removedEnd = index + 1
        }

        kind == LineKind.Added -> {
          if (!inAddedRun) addedStart = index
          addedEnd = index + 1
        }

        kind == LineKind.NoNewlineAtEof && inAddedRun -> addedEnd = index + 1

        kind == LineKind.NoNewlineAtEof && inRemovedRun -> removedEnd = index + 1

        else -> {
          flush()
          emit(index, index)
        }
      }
    }
    flush()
  }

  fun snapshot(): SplitRows = SplitRows(left.snapshot(), right.snapshot(), splitIndex.snapshot())

  private fun emit(l: Int, r: Int) {
    val row = left.size
    left.add(l)
    right.add(r)
    if (l >= 0) splitIndex[l] = row
    if (r >= 0) splitIndex[r] = row
  }
}

fun splitRowsOf(rows: List<DiffRow>): SplitRows = SplitRowsBuilder().apply { append(rows, 0) }.snapshot()

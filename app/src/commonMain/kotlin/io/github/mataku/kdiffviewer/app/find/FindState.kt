package io.github.mataku.kdiffviewer.app.find

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class FindCommand { Show, Next, Previous }

class FindRequests {
  private val flow = MutableSharedFlow<FindCommand>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val requests: SharedFlow<FindCommand> = flow.asSharedFlow()

  fun request(command: FindCommand): Boolean = flow.tryEmit(command)
}

@Stable
class FindState {
  private var queryText by mutableStateOf("")
  var query: String
    get() = queryText
    set(value) {
      queryText = value.take(FIND_QUERY_LIMIT)
    }

  var open by mutableStateOf(false)
    private set
  var all by mutableStateOf(FindMatches.Empty)
    private set
  var visible by mutableStateOf(FindMatches.Empty)
    private set
  var current by mutableIntStateOf(-1)
    private set
  var focusRequests by mutableIntStateOf(0)
    private set
  private var liveLoadId by mutableLongStateOf(-1L)
  private var liveGeneration by mutableLongStateOf(-1L)

  val isLive: Boolean get() = all.isFor(liveLoadId, liveGeneration)

  fun setLive(loadId: Long, rowsGeneration: Long) {
    liveLoadId = loadId
    liveGeneration = rowsGeneration
  }

  fun show() {
    open = true
    focusRequests++
  }

  fun close() {
    open = false
    current = -1
  }

  fun dismiss() {
    if (query.isNotEmpty()) query = "" else close()
  }

  fun onMatches(matches: FindMatches, keepFile: (fileIndex: Int) -> Boolean, anchorRow: Int): Int? {
    val fresh = current < 0 || matches.key?.query != all.key?.query
    val previousRow = if (fresh) anchorRow else visible.rowAt(current)
    val previousStart = if (fresh) 0 else visible.startAt(current)
    all = matches
    visible = matches.filter(keepFile)
    current = visible.indexAtOrAfterWrapping(previousRow, previousStart)
    return if (fresh && current >= 0) visible.rowAt(current) else null
  }

  fun onVisibility(loadId: Long, rowsGeneration: Long, keepFile: (fileIndex: Int) -> Boolean) {
    if (!all.isFor(loadId, rowsGeneration)) return
    val previousRow = if (current >= 0) visible.rowAt(current) else -1
    val previousStart = if (current >= 0) visible.startAt(current) else 0
    visible = all.filter(keepFile)
    current = if (previousRow < 0) -1 else visible.indexAtOrAfterWrapping(previousRow, previousStart)
  }

  fun step(forward: Boolean): Int? {
    val count = visible.count
    if (count == 0) return null
    current = when {
      current < 0 -> if (forward) 0 else count - 1
      forward -> (current + 1) % count
      else -> (current - 1 + count) % count
    }
    return visible.rowAt(current)
  }

  fun countText(): String? {
    if (query.isEmpty()) return null
    val count = visible.count
    if (count == 0) return "No results"
    val total = groupThousands(count) + if (visible.capped) "+" else ""
    return "${current + 1} of $total"
  }

  private fun FindMatches.indexAtOrAfterWrapping(row: Int, start: Int): Int {
    if (count == 0) return -1
    var index = firstIndexAtOrAfter(row)
    while (index < count && rowAt(index) == row && startAt(index) < start) index++
    return if (index < count) index else 0
  }
}

internal fun groupThousands(value: Int): String {
  val digits = value.toString()
  val builder = StringBuilder(digits.length + digits.length / 3)
  digits.forEachIndexed { index, char ->
    if (index > 0 && (digits.length - index) % 3 == 0) builder.append(',')
    builder.append(char)
  }
  return builder.toString()
}

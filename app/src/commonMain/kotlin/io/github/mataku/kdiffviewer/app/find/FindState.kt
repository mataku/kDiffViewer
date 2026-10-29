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
  var matches by mutableStateOf(FindMatches.Empty)
    private set
  var current by mutableIntStateOf(-1)
    private set
  var focusRequests by mutableIntStateOf(0)
    private set
  private var liveLoadId by mutableLongStateOf(-1L)
  private var liveGeneration by mutableLongStateOf(-1L)

  val isPaintable: Boolean get() = matches.isFor(liveLoadId, liveGeneration)

  val isLive: Boolean get() = isPaintable && matches.key?.query == query

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

  fun onMatches(matches: FindMatches, anchorRow: Int): Int? {
    val previous = this.matches
    val fresh = current < 0 || matches.key?.query != previous.key?.query
    val previousRow = if (fresh) anchorRow else previous.rowAt(current)
    val previousStart = if (fresh) 0 else previous.startAt(current)
    this.matches = matches
    current = matches.indexAtOrAfterWrapping(previousRow, previousStart)
    return if (fresh && current >= 0) matches.rowAt(current) else null
  }

  fun step(forward: Boolean): Int? {
    val count = matches.count
    if (count == 0 || !isLive) return null
    current = when {
      current < 0 -> if (forward) 0 else count - 1
      forward -> (current + 1) % count
      else -> (current - 1 + count) % count
    }
    return matches.rowAt(current)
  }

  fun countText(): String? {
    if (query.isEmpty() || matches.key?.query.isNullOrEmpty()) return null
    val count = matches.count
    if (count == 0) return "No results"
    val total = groupThousands(count) + if (matches.capped) "+" else ""
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

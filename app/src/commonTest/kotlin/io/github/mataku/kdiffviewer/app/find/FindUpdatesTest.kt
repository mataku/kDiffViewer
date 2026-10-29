package io.github.mataku.kdiffviewer.app.find

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindUpdatesTest {
  private fun matchesFor(query: String, state: Int) = FindMatches.Empty.updatedFor(emptyList(), FindKey(query, state.toLong(), 0))

  @Test
  fun resultsArriveWhileRowsKeepStreaming() = runTest {
    val arrivals = mutableListOf<Long>()
    val states = flow {
      repeat(20) {
        emit(it)
        delay(16)
      }
    }
    val updates = mutableListOf<FindMatches>()
    findMatchUpdates(flowOf("foo"), flowOf(true), states) { _, query, state ->
      delay(50)
      matchesFor(query, state).also { arrivals += currentTime }
    }.toList(updates)
    assertTrue(arrivals.first() < 320, "first result at ${arrivals.first()} ms, before streaming ends")
    assertEquals(19L, updates.last().key?.loadId)
  }

  @Test
  fun queryEditsAreDebounced() = runTest {
    val scanned = mutableListOf<String>()
    val queries = flow {
      emit("f")
      delay(30)
      emit("fo")
      delay(30)
      emit("foo")
    }
    findMatchUpdates(queries, flowOf(true), flowOf(0)) { _, query, state ->
      scanned += query
      matchesFor(query, state)
    }.toList()
    assertEquals(listOf("foo"), scanned)
  }

  @Test
  fun closedPanelDoesNotScan() = runTest {
    val scanned = mutableListOf<String>()
    findMatchUpdates(flowOf("foo"), flowOf(false), flowOf(0)) { _, query, state ->
      scanned += query
      matchesFor(query, state)
    }.toList()
    assertEquals(emptyList(), scanned)
  }

  @Test
  fun scanReceivesPreviousResult() = runTest {
    val previousKeys = mutableListOf<Long?>()
    val states = flow {
      emit(1)
      delay(FIND_DEBOUNCE_MILLIS * 2)
      emit(2)
    }
    findMatchUpdates(flowOf("foo"), flowOf(true), states) { previous, query, state ->
      previousKeys += previous.key?.loadId
      matchesFor(query, state)
    }.toList()
    assertEquals(listOf(null, 1L), previousKeys)
  }
}

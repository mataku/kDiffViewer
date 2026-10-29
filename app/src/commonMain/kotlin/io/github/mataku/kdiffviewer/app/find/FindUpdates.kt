package io.github.mataku.kdiffviewer.app.find

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

const val FIND_DEBOUNCE_MILLIS = 100L

@OptIn(ExperimentalCoroutinesApi::class)
fun <S> findMatchUpdates(
  queries: Flow<String>,
  opens: Flow<Boolean>,
  states: Flow<S>,
  scan: suspend (previous: FindMatches, query: String, state: S) -> FindMatches,
): Flow<FindMatches> = flow {
  var previous = FindMatches.Empty
  combine(
    queries.transformLatest { query ->
      if (query.isNotEmpty()) delay(FIND_DEBOUNCE_MILLIS)
      emit(query)
    },
    opens,
    states,
  ) { query, open, state -> Triple(query, open, state) }
    .conflate()
    .collect { (query, open, state) ->
      if (!open) return@collect
      previous = scan(previous, query, state)
      emit(previous)
    }
}

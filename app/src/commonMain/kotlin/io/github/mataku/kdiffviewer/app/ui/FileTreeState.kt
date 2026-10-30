package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class FileTreeState {
  val listState = LazyListState()

  var query by mutableStateOf("")
    private set

  var collapsedDirectories by mutableStateOf(emptySet<String>())
    private set

  fun changeQuery(query: String) {
    this.query = query
  }

  fun toggleDirectory(path: String) {
    collapsedDirectories = if (path in collapsedDirectories) collapsedDirectories - path else collapsedDirectories + path
  }
}

@Composable
fun rememberFileTreeState(key: Any?): FileTreeState = remember(key) { FileTreeState() }

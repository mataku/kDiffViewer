package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.git.DiffLoadException
import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

sealed interface LoadStatus {
  data object Idle : LoadStatus
  data object Loading : LoadStatus
  data object Done : LoadStatus
  data class Failed(val error: ViewerError) : LoadStatus
}

sealed interface ViewerError {
  data object GitNotFound : ViewerError
  data class NotARepository(val path: String) : ViewerError
  data class GitFailed(val stderr: String, val exitCode: Int? = null) : ViewerError
}

data class ViewerUiState(
  val repo: RepoPath? = null,
  val status: LoadStatus = LoadStatus.Idle,
  val files: List<FileDiff> = emptyList(),
  val rows: List<DiffRow> = emptyList(),
  val fileHeaderRowIndex: List<Int> = emptyList(),
  val tree: List<TreeRow> = emptyList(),
  val loadId: Long = 0,
)

class DiffViewerState(
  private val source: DiffSource?,
  private val scope: CoroutineScope,
  private val batchWindowMillis: Long = 16,
) {
  private val mutableUiState = MutableStateFlow(ViewerUiState())
  val uiState: StateFlow<ViewerUiState> = mutableUiState.asStateFlow()

  private var loadJob: Job? = null
  private var nextLoadId = 0L

  @Volatile
  private var currentLoad: Any? = null

  fun open(repo: RepoPath) {
    loadJob?.cancel()
    loadJob = null
    val token = Any()
    currentLoad = token
    val loadId = ++nextLoadId
    val diffSource = source
    if (diffSource == null) {
      mutableUiState.value = ViewerUiState(repo = repo, status = LoadStatus.Failed(ViewerError.GitNotFound), loadId = loadId)
      return
    }
    mutableUiState.value = ViewerUiState(repo = repo, status = LoadStatus.Loading, loadId = loadId)
    val accumulator = LoadAccumulator()
    loadJob = scope.launch {
      try {
        load(diffSource, repo, token, accumulator)
        updateIfCurrent(token) { it.copy(status = LoadStatus.Done, tree = accumulator.tree.rows()) }
      } catch (e: Throwable) {
        ensureActive()
        updateIfCurrent(token) { it.copy(status = LoadStatus.Failed(e.toViewerError()), tree = accumulator.tree.rows()) }
      }
    }
  }

  fun reload() {
    val repo = mutableUiState.value.repo ?: return
    open(repo)
  }

  private suspend fun load(diffSource: DiffSource, repo: RepoPath, token: Any, accumulator: LoadAccumulator) = coroutineScope {
    val channel = Channel<FileDiff>(Channel.UNLIMITED)
    launch {
      var failure: Throwable? = null
      try {
        diffSource.load(repo).collect { channel.send(it) }
      } catch (e: Throwable) {
        failure = e
      } finally {
        channel.close(failure)
      }
    }
    try {
      val buffer = ArrayList<FileDiff>()
      while (true) {
        val first = channel.receiveCatching()
        if (first.isClosed) {
          first.exceptionOrNull()?.let { throw it }
          return@coroutineScope
        }
        buffer += first.getOrThrow()
        delay(batchWindowMillis)
        val closure = drainInto(channel, buffer)
        publish(buffer, token, accumulator)
        buffer.clear()
        if (closure != null) {
          closure.exceptionOrNull()?.let { throw it }
          return@coroutineScope
        }
      }
    } finally {
      channel.cancel()
    }
  }

  private fun drainInto(channel: Channel<FileDiff>, buffer: MutableList<FileDiff>): ChannelResult<FileDiff>? {
    while (true) {
      val next = channel.tryReceive()
      when {
        next.isSuccess -> buffer += next.getOrThrow()
        next.isClosed -> return next
        else -> return null
      }
    }
  }

  private inline fun updateIfCurrent(token: Any, transform: (ViewerUiState) -> ViewerUiState) {
    mutableUiState.update { current -> if (currentLoad === token) transform(current) else current }
  }

  private fun publish(batch: List<FileDiff>, token: Any, accumulator: LoadAccumulator) {
    accumulator.append(batch)
    val files = accumulator.files.snapshot()
    val rows = accumulator.rows.snapshot()
    val headerIndex = accumulator.fileHeaderRowIndex.snapshot()
    val tree = accumulator.tree.rowsIfDue()
    updateIfCurrent(token) { current ->
      current.copy(files = files, rows = rows, fileHeaderRowIndex = headerIndex, tree = tree)
    }
  }

  private class LoadAccumulator {
    val files = AppendBuffer<FileDiff>()
    val rows = AppendBuffer<DiffRow>()
    val fileHeaderRowIndex = AppendBuffer<Int>()
    val tree = FileTreeBuilder()
    private var fileCount = 0
    private var rowCount = 0

    fun append(batch: List<FileDiff>) {
      val newRows = flattenRows(batch, fileCount)
      newRows.forEachIndexed { offset, row ->
        if (row is DiffRow.FileHeader) fileHeaderRowIndex.add(rowCount + offset)
      }
      files.addAll(batch)
      rows.addAll(newRows)
      tree.add(batch)
      fileCount += batch.size
      rowCount += newRows.size
    }
  }

  private fun Throwable.toViewerError(): ViewerError = when (this) {
    is DiffLoadException.NotARepository -> ViewerError.NotARepository(path)
    is DiffLoadException.GitFailed -> ViewerError.GitFailed(stderr, exitCode)
    else -> ViewerError.GitFailed(message ?: toString())
  }
}

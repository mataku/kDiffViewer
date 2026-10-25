package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.git.DiffLoadException
import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LoadStatus {
    data object Idle : LoadStatus
    data object Loading : LoadStatus
    data object Done : LoadStatus
    data class Failed(val error: ViewerError) : LoadStatus
}

sealed interface ViewerError {
    data object GitNotFound : ViewerError
    data class NotARepository(val path: String) : ViewerError
    data class GitFailed(val stderr: String) : ViewerError
}

data class ViewerUiState(
    val repo: RepoPath? = null,
    val status: LoadStatus = LoadStatus.Idle,
    val files: List<FileDiff> = emptyList(),
    val rows: List<DiffRow> = emptyList(),
    val fileHeaderRowIndex: List<Int> = emptyList(),
    val tree: List<TreeRow> = emptyList(),
)

class DiffViewerState(
    private val source: DiffSource?,
    private val scope: CoroutineScope,
    private val batchWindowMillis: Long = 16,
) {
    private val mutableUiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = mutableUiState.asStateFlow()

    private var loadJob: Job? = null

    fun open(repo: RepoPath) {
        loadJob?.cancel()
        loadJob = null
        val diffSource = source
        if (diffSource == null) {
            mutableUiState.value = ViewerUiState(repo = repo, status = LoadStatus.Failed(ViewerError.GitNotFound))
            return
        }
        mutableUiState.value = ViewerUiState(repo = repo, status = LoadStatus.Loading)
        loadJob = scope.launch {
            try {
                load(diffSource, repo)
                mutableUiState.update { it.copy(status = LoadStatus.Done) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                mutableUiState.update { it.copy(status = LoadStatus.Failed(e.toViewerError())) }
            }
        }
    }

    fun reload() {
        val repo = mutableUiState.value.repo ?: return
        open(repo)
    }

    private suspend fun load(diffSource: DiffSource, repo: RepoPath) = coroutineScope {
        val channel = Channel<FileDiff>(Channel.UNLIMITED)
        launch {
            try {
                diffSource.load(repo).collect { channel.send(it) }
                channel.close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                channel.close(e)
            }
        }
        val buffer = ArrayList<FileDiff>()
        while (true) {
            val first = channel.receiveCatching()
            if (first.isClosed) {
                first.exceptionOrNull()?.let { throw it }
                return@coroutineScope
            }
            buffer += first.getOrThrow()
            var closure: ChannelResult<FileDiff>? = null
            withTimeoutOrNull(batchWindowMillis) {
                while (true) {
                    val next = channel.receiveCatching()
                    if (next.isClosed) {
                        closure = next
                        break
                    }
                    buffer += next.getOrThrow()
                }
            }
            publish(buffer)
            buffer.clear()
            closure?.let { closed ->
                closed.exceptionOrNull()?.let { throw it }
                return@coroutineScope
            }
        }
    }

    private fun publish(batch: List<FileDiff>) {
        val newFiles = batch.toList()
        mutableUiState.update { current ->
            val startIndex = current.files.size
            val newRows = flattenRows(newFiles, startIndex)
            val rows = current.rows + newRows
            val headerIndex = current.fileHeaderRowIndex.toMutableList()
            newRows.forEachIndexed { offset, row ->
                if (row is DiffRow.FileHeader) headerIndex += current.rows.size + offset
            }
            val files = current.files + newFiles
            current.copy(
                files = files,
                rows = rows,
                fileHeaderRowIndex = headerIndex,
                tree = buildFileTree(files),
            )
        }
    }

    private fun Throwable.toViewerError(): ViewerError = when (this) {
        is DiffLoadException.NotARepository -> ViewerError.NotARepository(path)
        is DiffLoadException.GitFailed -> ViewerError.GitFailed(stderr)
        else -> ViewerError.GitFailed(message ?: toString())
    }
}

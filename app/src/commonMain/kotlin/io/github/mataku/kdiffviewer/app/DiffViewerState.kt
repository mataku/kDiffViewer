package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.app.highlight.HighlightEntry
import io.github.mataku.kdiffviewer.app.highlight.HighlightPipeline
import io.github.mataku.kdiffviewer.app.highlight.HighlightRun
import io.github.mataku.kdiffviewer.app.highlight.HighlightSlot
import io.github.mataku.kdiffviewer.core.git.DiffLoadException
import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import io.github.mataku.kdiffviewer.core.model.displayPath
import io.github.mataku.kdiffviewer.core.parse.DiffBudget
import io.github.mataku.kdiffviewer.core.parse.DiffLimits
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Immutable
sealed interface LoadStatus {
  data object Idle : LoadStatus
  data object Loading : LoadStatus
  data object Done : LoadStatus

  @Immutable
  data class Failed(val error: ViewerError) : LoadStatus
}

@Immutable
sealed interface ViewerError {
  data object GitNotFound : ViewerError

  @Immutable
  data class GitUnavailable(val path: String) : ViewerError

  @Immutable
  data class NotARepository(val path: String) : ViewerError

  @Immutable
  data class UnknownBase(val ref: String) : ViewerError

  @Immutable
  data class NoCommonAncestor(val ref: String) : ViewerError

  @Immutable
  data class InvalidLaunchArgs(val message: String) : ViewerError

  @Immutable
  data class GitFailed(val stderr: String, val exitCode: Int? = null) : ViewerError
}

@Immutable
data class ViewerUiState(
  val repo: RepoPath? = null,
  val status: LoadStatus = LoadStatus.Idle,
  val files: List<FileDiff> = emptyList(),
  val rows: List<DiffRow> = emptyList(),
  val fileHeaderRowIndex: List<Int> = emptyList(),
  val tree: List<TreeRow> = emptyList(),
  val loadId: Long = 0,
  val highlights: List<HighlightSlot> = emptyList(),
  val head: HeadRef? = null,
  val root: RepoPath? = null,
  val fingerprints: List<Long> = emptyList(),
  val collapsed: Set<CollapsedFile> = emptySet(),
  val expanded: Set<CollapsedFile> = emptySet(),
  val baseSpec: BaseSpec = BaseSpec.Default,
  val base: ResolvedBase? = null,
  val truncated: Boolean = false,
  val split: SplitRows = SplitRows.Empty,
)

@Immutable
data class CollapsedFile(val displayPath: String, val fingerprint: Long)

fun ViewerUiState.isLargeDiff(fileIndex: Int): Boolean {
  val headerRow = fileHeaderRowIndex.getOrNull(fileIndex) ?: return false
  return (rows.getOrNull(headerRow) as? DiffRow.FileHeader)?.largeDiff == true
}

fun ViewerUiState.isCollapsed(fileIndex: Int): Boolean {
  val entry = collapsedFileAt(fileIndex) ?: return false
  return if (isLargeDiff(fileIndex)) entry !in expanded else entry in collapsed
}

private fun ViewerUiState.collapsedFileAt(fileIndex: Int): CollapsedFile? {
  val file = files.getOrNull(fileIndex) ?: return null
  val fingerprint = fingerprints.getOrNull(fileIndex) ?: return null
  return CollapsedFile(file.displayPath, fingerprint)
}

class DiffViewerState(
  private var source: DiffSource?,
  private val scope: CoroutineScope,
  private val batchWindowMillis: Long = 16,
  private var highlightPipeline: HighlightPipeline<*>? = null,
  private val reloadHoldMillis: Long = 300,
  private var missingSourceError: ViewerError = ViewerError.GitNotFound,
  private val loadContext: CoroutineContext = EmptyCoroutineContext,
  private val largeDiffLines: Int = LARGE_DIFF_LINES,
  private val diffLimits: DiffLimits = DiffLimits(),
) {
  private val mutableUiState = MutableStateFlow(ViewerUiState())
  val uiState: StateFlow<ViewerUiState> = mutableUiState.asStateFlow()

  private var loadJob: Job? = null
  private var highlightRun: HighlightRun? = null
  private var nextLoadId = 0L

  @Volatile
  private var currentLoad: Any? = null

  fun open(repo: RepoPath, base: BaseSpec = BaseSpec.Default): Long {
    loadJob?.cancel()
    loadJob = null
    highlightRun?.cancel()
    highlightRun = null
    val token = Any()
    currentLoad = token
    val loadId = ++nextLoadId
    val diffSource = source
    if (diffSource == null) {
      mutableUiState.value = ViewerUiState(repo = repo, status = LoadStatus.Failed(missingSourceError), loadId = loadId, baseSpec = base)
      return loadId
    }
    val previous = mutableUiState.value
    val keepPrevious = previous.repo == repo && previous.status !is LoadStatus.Failed
    mutableUiState.value = if (keepPrevious) {
      previous.copy(status = LoadStatus.Loading, baseSpec = base, base = previous.base.takeIf { previous.baseSpec == base })
    } else {
      ViewerUiState(
        repo = repo,
        status = LoadStatus.Loading,
        loadId = loadId,
        head = previous.head.takeIf { previous.repo == repo },
        root = previous.root.takeIf { previous.repo == repo },
        baseSpec = base,
        base = previous.base.takeIf { previous.repo == repo && previous.baseSpec == base },
      )
    }
    val knownRoot = mutableUiState.value.root
    val accumulator = LoadAccumulator(loadId, holding = keepPrevious, largeDiffLines)
    loadJob = scope.launch {
      launch {
        val head = try {
          diffSource.head(repo)
        } catch (e: Exception) {
          ensureActive()
          null
        }
        updateIfCurrent(token) { it.copy(head = head) }
      }
      if (knownRoot == null) {
        launch {
          val root = try {
            diffSource.root(repo)
          } catch (e: Exception) {
            ensureActive()
            null
          }
          if (root != null) updateIfCurrent(token) { it.copy(root = root) }
        }
      }
      val holdJob = if (keepPrevious) {
        launch {
          delay(reloadHoldMillis)
          accumulator.holding = false
          updateIfCurrent(token) { it.withContentOf(accumulator, accumulator.tree.rowsIfDue()) }
        }
      } else {
        null
      }
      try {
        val resolved = diffSource.resolveBase(repo, base)
        ensureActive()
        updateIfCurrent(token) { it.copy(base = resolved) }
        val run = highlightPipeline?.start(scope, repo, resolved)
        highlightRun = run
        load(diffSource, repo, resolved, token, accumulator, run)
        holdJob?.cancel()
        updateIfCurrent(token) {
          val done = it.withContentOf(accumulator, accumulator.tree.rows())
          done.copy(status = LoadStatus.Done, collapsed = done.pruned(done.collapsed), expanded = done.pruned(done.expanded))
        }
      } catch (e: Throwable) {
        ensureActive()
        holdJob?.cancel()
        updateIfCurrent(token) {
          it.withContentOf(accumulator, accumulator.tree.rows()).copy(status = LoadStatus.Failed(e.toViewerError()))
        }
      }
    }
    return loadId
  }

  fun fail(repo: RepoPath, error: ViewerError) {
    loadJob?.cancel()
    loadJob = null
    highlightRun?.cancel()
    highlightRun = null
    currentLoad = null
    mutableUiState.value = ViewerUiState(repo = repo, status = LoadStatus.Failed(error), loadId = ++nextLoadId)
  }

  fun reload(): Long? {
    val repo = mutableUiState.value.repo ?: return null
    return open(repo, mutableUiState.value.baseSpec)
  }

  suspend fun switchBase(spec: BaseSpec): ViewerError? {
    val repo = mutableUiState.value.repo
    val diffSource = source
    if (repo == null || diffSource == null) return missingSourceError
    if (spec is BaseSpec.Ref && spec.ref.startsWith("-")) return ViewerError.UnknownBase(spec.ref)
    val generation = nextLoadId
    try {
      withContext(loadContext) { diffSource.resolveBase(repo, spec) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      return e.toViewerError()
    }
    if (nextLoadId == generation) open(repo, spec)
    return null
  }

  suspend fun branches(): List<BranchRef> {
    val diffSource = source
    val repo = mutableUiState.value.repo
    if (diffSource == null || repo == null) return emptyList()
    return withContext(loadContext) { diffSource.branches(repo) }
  }

  fun setBackend(source: DiffSource?, highlightPipeline: HighlightPipeline<*>?, missingSourceError: ViewerError = ViewerError.GitNotFound) {
    this.source = source
    this.missingSourceError = missingSourceError
    this.highlightPipeline = highlightPipeline
    reload()
  }

  fun toggleCollapsed(fileIndex: Int) {
    mutableUiState.update { current ->
      val entry = current.collapsedFileAt(fileIndex) ?: return@update current
      if (current.isLargeDiff(fileIndex)) {
        current.copy(expanded = if (entry in current.expanded) current.expanded - entry else current.expanded + entry)
      } else {
        current.copy(collapsed = if (entry in current.collapsed) current.collapsed - entry else current.collapsed + entry)
      }
    }
  }

  fun expand(fileIndex: Int) {
    mutableUiState.update { current ->
      if (!current.isCollapsed(fileIndex)) return@update current
      val entry = current.collapsedFileAt(fileIndex) ?: return@update current
      if (current.isLargeDiff(fileIndex)) current.copy(expanded = current.expanded + entry) else current.copy(collapsed = current.collapsed - entry)
    }
  }

  private fun ViewerUiState.pruned(entries: Set<CollapsedFile>): Set<CollapsedFile> {
    if (entries.isEmpty()) return entries
    val kept = HashSet<CollapsedFile>()
    files.forEachIndexed { index, file ->
      val entry = CollapsedFile(file.displayPath, fingerprints[index])
      if (entry in entries) kept += entry
    }
    return if (kept.size == entries.size) entries else kept
  }

  private suspend fun load(diffSource: DiffSource, repo: RepoPath, base: ResolvedBase, token: Any, accumulator: LoadAccumulator, run: HighlightRun?) = coroutineScope {
    val channel = Channel<LoadedFile>(Channel.UNLIMITED)
    val budget = DiffBudget(diffLimits)
    launch {
      var failure: Throwable? = null
      try {
        diffSource.load(repo, base)
          .transformWhile { file ->
            val admitted = budget.admit(file)
            if (admitted) emit(file) else accumulator.truncated = true
            admitted
          }
          .map { LoadedFile(it, it.fingerprint()) }
          .flowOn(loadContext)
          .collect { channel.send(it) }
      } catch (e: Throwable) {
        failure = e
      } finally {
        channel.close(failure)
      }
    }
    try {
      val buffer = ArrayList<LoadedFile>()
      while (true) {
        val first = channel.receiveCatching()
        if (first.isClosed) {
          first.exceptionOrNull()?.let { throw it }
          return@coroutineScope
        }
        buffer += first.getOrThrow()
        delay(batchWindowMillis)
        val closure = drainInto(channel, buffer)
        publish(buffer, token, accumulator, run)
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

  private fun drainInto(channel: Channel<LoadedFile>, buffer: MutableList<LoadedFile>): ChannelResult<LoadedFile>? {
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

  private fun publish(batch: List<LoadedFile>, token: Any, accumulator: LoadAccumulator, run: HighlightRun?) {
    val entries = accumulator.append(batch)
    run?.submit(entries)
    if (accumulator.holding) return
    updateIfCurrent(token) { it.withContentOf(accumulator, accumulator.tree.rowsIfDue()) }
  }

  private fun ViewerUiState.withContentOf(accumulator: LoadAccumulator, tree: List<TreeRow>): ViewerUiState = copy(
    files = accumulator.files.snapshot(),
    rows = accumulator.rows.snapshot(),
    split = accumulator.split.snapshot(),
    fileHeaderRowIndex = accumulator.fileHeaderRowIndex.snapshot(),
    tree = tree,
    highlights = accumulator.highlights.snapshot(),
    fingerprints = accumulator.fingerprints.snapshot(),
    loadId = accumulator.loadId,
    truncated = accumulator.truncated,
  )

  private class LoadedFile(val file: FileDiff, val fingerprint: Long)

  private class LoadAccumulator(val loadId: Long, var holding: Boolean, private val largeDiffLines: Int) {
    val files = AppendBuffer<FileDiff>()
    val rows = AppendBuffer<DiffRow>()
    val split = SplitRowsBuilder()
    val fileHeaderRowIndex = AppendBuffer<Int>()
    val highlights = AppendBuffer<HighlightSlot>()
    val fingerprints = AppendBuffer<Long>()
    val tree = FileTreeBuilder()

    @Volatile
    var truncated = false
    private var fileCount = 0
    private var rowCount = 0

    fun append(loaded: List<LoadedFile>): List<HighlightEntry> {
      val batch = loaded.map { it.file }
      val newRows = flattenRows(batch, fileCount, largeDiffLines)
      newRows.forEachIndexed { offset, row ->
        if (row is DiffRow.FileHeader) fileHeaderRowIndex.add(rowCount + offset)
      }
      val entries = batch.mapIndexed { offset, file -> HighlightEntry(fileCount + offset, file, HighlightSlot()) }
      files.addAll(batch)
      for (file in loaded) fingerprints.add(file.fingerprint)
      highlights.addAll(entries.map { it.slot })
      rows.addAll(newRows)
      split.append(rows.snapshot(), rowCount)
      tree.add(batch)
      fileCount += batch.size
      rowCount += newRows.size
      return entries
    }
  }

  private fun Throwable.toViewerError(): ViewerError = when (this) {
    is DiffLoadException.NotARepository -> ViewerError.NotARepository(path)
    is DiffLoadException.UnknownBase -> ViewerError.UnknownBase(ref)
    is DiffLoadException.NoCommonAncestor -> ViewerError.NoCommonAncestor(ref)
    is DiffLoadException.GitFailed -> ViewerError.GitFailed(stderr, exitCode)
    else -> ViewerError.GitFailed(message ?: toString())
  }
}

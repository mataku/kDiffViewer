package io.github.mataku.kdiffviewer.app.highlight

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.compose.highlight.core.HighlightedLine
import io.github.mataku.kdiffviewer.core.git.ContentSession
import io.github.mataku.kdiffviewer.core.git.ContentSource
import io.github.mataku.kdiffviewer.core.git.UNTRACKED_SIZE_LIMIT
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.model.highlightRanges
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

@Stable
class HighlightSlot {
  var value: FileHighlight? by mutableStateOf(null)
}

@Immutable
class FileHighlight(val spans: Array<Array<List<HighlightSpan>?>>)

class HighlightEntry(val fileIndex: Int, val file: FileDiff, val slot: HighlightSlot)

fun interface LineHighlighter<L> {
  fun highlight(code: String, language: L, ranges: List<IntRange>): List<HighlightedLine>
}

class HighlightPipeline<L>(
  private val contentSource: ContentSource,
  private val languages: LanguageResolver<L>,
  private val highlighter: LineHighlighter<L>,
  private val dispatcher: CoroutineDispatcher,
  private val workers: Int = max(1, availableProcessors / 2),
  private val onFailure: (Throwable) -> Unit = { it.printStackTrace() },
) {
  fun start(scope: CoroutineScope, repo: RepoPath): HighlightRun {
    val entries = Channel<HighlightEntry>(Channel.UNLIMITED)
    val runJob = SupervisorJob(scope.coroutineContext[Job])
    val handler = CoroutineExceptionHandler { _, throwable ->
      runJob.cancel()
      onFailure(throwable)
    }
    val sessions = SessionHolder(contentSource, repo)
    CoroutineScope(scope.coroutineContext + runJob + dispatcher + handler).launch {
      try {
        coroutineScope {
          repeat(workers) {
            launch { work(entries, sessions) }
          }
        }
      } finally {
        entries.cancel()
        sessions.close()
      }
    }
    return HighlightRun(entries, runJob)
  }

  private suspend fun work(entries: Channel<HighlightEntry>, sessions: SessionHolder) {
    while (true) {
      currentCoroutineContext().ensureActive()
      val entry = entries.receive()
      val highlight = highlight(entry.file, sessions)
      currentCoroutineContext().ensureActive()
      if (highlight != null) entry.slot.value = highlight
    }
  }

  private suspend fun highlight(file: FileDiff, sessions: SessionHolder): FileHighlight? {
    val hunks = (file.content as? DiffContent.Text)?.hunks ?: return null
    val old = highlightSide(file, Side.Old, file.oldPath, sessions)
    val new = highlightSide(file, Side.New, file.newPath, sessions)
    if (old == null && new == null) return null

    var any = false
    val spans = Array(hunks.size) { hunkIndex ->
      val lines = hunks[hunkIndex].lines
      Array(lines.size) { lineIndex ->
        spansFor(lines[lineIndex], old, new)?.also { any = true }
      }
    }
    return if (any) FileHighlight(spans) else null
  }

  private fun spansFor(line: DiffLine, old: List<HighlightedLine>?, new: List<HighlightedLine>?): List<HighlightSpan>? {
    val highlighted = when (line.kind) {
      LineKind.Removed -> line.oldLineNo?.let { old?.findLine(it) }
      LineKind.Added, LineKind.Context -> line.newLineNo?.let { new?.findLine(it) }
      LineKind.NoNewlineAtEof -> null
    } ?: return null
    return if (highlighted.text == line.text && highlighted.spans.isNotEmpty()) highlighted.spans else null
  }

  private suspend fun highlightSide(file: FileDiff, side: Side, path: String?, sessions: SessionHolder): List<HighlightedLine>? {
    if (path == null) return null
    val ranges = file.highlightRanges(side)
    if (ranges.isEmpty()) return null
    val language = languages.languageFor(path) ?: return null
    val text = sessions.get().read(file, side, UNTRACKED_SIZE_LIMIT) ?: return null
    return highlighter.highlight(text, language, ranges)
  }

  private fun List<HighlightedLine>.findLine(lineNumber: Int): HighlightedLine? {
    val index = binarySearch { it.lineNumber.compareTo(lineNumber) }
    return if (index >= 0) this[index] else null
  }
}

private class SessionHolder(private val contentSource: ContentSource, private val repo: RepoPath) {
  private val mutex = Mutex()
  private var session: ContentSession? = null

  suspend fun get(): ContentSession = mutex.withLock {
    session ?: contentSource.open(repo).also { session = it }
  }

  fun close() {
    session?.close()
  }
}

class HighlightRun internal constructor(
  private val entries: Channel<HighlightEntry>,
  private val job: Job,
) {
  fun submit(entries: List<HighlightEntry>) {
    for (entry in entries) {
      if (this.entries.trySend(entry).isFailure) return
    }
  }

  fun cancel() {
    job.cancel()
    entries.cancel()
  }
}

package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.app.markdown.SourceParse
import io.github.mataku.kdiffviewer.app.markdown.parseMarkdownSource
import io.github.mataku.kdiffviewer.core.git.WorkingTreeReader
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.displayPath

internal sealed interface RenderOutcome {
  class Rows(val rows: List<DiffRow>) : RenderOutcome

  class Failed(val error: ExpansionError) : RenderOutcome
}

internal suspend fun renderFile(original: FileDiff, shown: FileDiff, fileIndex: Int, reader: WorkingTreeReader, root: RepoPath): RenderOutcome = when (val read = readExpansionText(original, reader, root)) {
  is ExpansionText.Failed -> RenderOutcome.Failed(
    when (read.error) {
      ExpansionError.TooLarge -> ExpansionError.RenderTooLarge
      ExpansionError.Stale -> ExpansionError.RenderStale
      else -> read.error
    },
  )

  is ExpansionText.Lines -> when (val parsed = parseMarkdownSource(read.lines.joinToString("\n"))) {
    is SourceParse.Blocks -> RenderOutcome.Rows(renderedBodyRows(fileIndex, shown, parsed.blocks))
    SourceParse.TooLarge -> RenderOutcome.Failed(ExpansionError.RenderTooLarge)
    SourceParse.Failed -> RenderOutcome.Failed(ExpansionError.RenderFailed)
  }
}

internal class BodySwap(
  val rows: List<DiffRow>,
  val headers: List<Int>,
  val split: SplitRows,
  val replaced: Map<Int, List<DiffRow>>,
)

internal fun ViewerUiState.bodySwap(bodies: Map<Int, List<DiffRow>>): BodySwap {
  val newRows = ArrayList<DiffRow>(rows.size)
  val headers = ArrayList<Int>(fileHeaderRowIndex.size)
  val replaced = HashMap<Int, List<DiffRow>>()
  for (fileIndex in fileHeaderRowIndex.indices) {
    val start = fileHeaderRowIndex[fileIndex]
    val end = fileHeaderRowIndex.getOrNull(fileIndex + 1) ?: rows.size
    headers += newRows.size
    val body = bodies[fileIndex]
    if (body == null) {
      newRows.addAll(rows.subList(start, end))
    } else {
      newRows += rows[start]
      newRows.addAll(body)
      replaced[fileIndex] = rows.subList(start + 1, end).toList()
    }
  }
  return BodySwap(newRows, headers, splitRowsOf(newRows), replaced)
}

internal fun ViewerUiState.withBodySwap(swap: BodySwap): ViewerUiState = copy(
  rows = swap.rows,
  fileHeaderRowIndex = swap.headers,
  split = swap.split,
  contentId = contentId + 1,
  rowsGeneration = rowsGeneration + 1,
)

internal class ReappliedRenders(
  val bodies: Map<Int, List<DiffRow>>,
  val paths: Set<String>,
  val errors: Map<CollapsedFile, ExpansionError>,
) {
  companion object {
    val None = ReappliedRenders(emptyMap(), emptySet(), emptyMap())
  }
}

internal suspend fun reapplyRendered(
  paths: Set<String>,
  originals: List<FileDiff>,
  shown: List<FileDiff>,
  fingerprints: List<Long>,
  reader: WorkingTreeReader,
  root: RepoPath,
): ReappliedRenders {
  val bodies = HashMap<Int, List<DiffRow>>()
  val rendered = HashSet<String>()
  val errors = HashMap<CollapsedFile, ExpansionError>()
  originals.forEachIndexed { fileIndex, original ->
    val file = shown[fileIndex]
    val path = file.displayPath
    if (path !in paths || !file.isRenderable()) return@forEachIndexed
    when (val outcome = renderFile(original, file, fileIndex, reader, root)) {
      is RenderOutcome.Rows -> {
        bodies[fileIndex] = outcome.rows
        rendered += path
      }

      is RenderOutcome.Failed -> errors[CollapsedFile(path, fingerprints[fileIndex])] = outcome.error
    }
  }
  return ReappliedRenders(bodies, rendered, errors)
}

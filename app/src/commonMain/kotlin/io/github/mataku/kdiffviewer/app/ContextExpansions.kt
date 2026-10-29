package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.app.highlight.HighlightEntry
import io.github.mataku.kdiffviewer.app.highlight.HighlightSlot
import io.github.mataku.kdiffviewer.core.git.UntrackedEntry
import io.github.mataku.kdiffviewer.core.git.WorkingTreeReader
import io.github.mataku.kdiffviewer.core.model.ContextExpansion
import io.github.mataku.kdiffviewer.core.model.ExpandedFile
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.contextMatches
import io.github.mataku.kdiffviewer.core.model.displayPath
import io.github.mataku.kdiffviewer.core.model.expandContext
import io.github.mataku.kdiffviewer.core.parse.splitLines
import kotlinx.coroutines.CancellationException

const val EXPAND_SIZE_LIMIT: Long = 4L * 1024 * 1024

internal class AppliedExpansion(val fileIndex: Int, val entry: CollapsedFile, val expansion: ContextExpansion, val expanded: ExpandedFile)

internal class ExpandedContent(
  val applied: List<AppliedExpansion>,
  val files: List<FileDiff>,
  val rows: List<DiffRow>,
  val headers: List<Int>,
  val split: SplitRows,
  val highlights: List<HighlightSlot>,
  val entries: List<HighlightEntry>,
)

internal class ReappliedExpansions(val applied: List<AppliedExpansion>, val errors: Map<CollapsedFile, String>)

internal sealed interface ExpansionText {
  class Lines(val lines: List<String>) : ExpansionText

  class Failed(val message: String) : ExpansionText
}

internal suspend fun readExpansionText(original: FileDiff, reader: WorkingTreeReader, root: RepoPath): ExpansionText {
  val path = original.newPath ?: return ExpansionText.Failed("Could not read this file")
  val read = try {
    reader.read(root.value, path, EXPAND_SIZE_LIMIT)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    UntrackedEntry.Unreadable
  }
  val lines = when (read) {
    is UntrackedEntry.Regular -> splitLines(read.bytes.decodeToString())
    UntrackedEntry.TooLarge -> return ExpansionText.Failed("Too large to expand")
    else -> return ExpansionText.Failed("Could not read this file")
  }
  if (!contextMatches(original, lines)) return ExpansionText.Failed("The file no longer matches this diff — reload to expand")
  return ExpansionText.Lines(lines)
}

internal suspend fun reapplyExpansions(
  previous: Map<CollapsedFile, ContextExpansion>,
  files: List<FileDiff>,
  fingerprints: List<Long>,
  reader: WorkingTreeReader,
  root: RepoPath,
): ReappliedExpansions {
  val applied = ArrayList<AppliedExpansion>()
  val errors = HashMap<CollapsedFile, String>()
  files.forEachIndexed { fileIndex, original ->
    val entry = CollapsedFile(original.displayPath, fingerprints[fileIndex])
    val kept = previous[entry] ?: return@forEachIndexed
    when (val read = readExpansionText(original, reader, root)) {
      is ExpansionText.Failed -> errors[entry] = read.message

      is ExpansionText.Lines -> {
        val expansion = kept.copy(newLineCount = read.lines.size)
        applied += AppliedExpansion(fileIndex, entry, expansion, expandContext(original, expansion, read.lines))
      }
    }
  }
  return ReappliedExpansions(applied, errors)
}

internal fun ViewerUiState.expandedContent(applied: List<AppliedExpansion>, largeDiffLines: Int): ExpandedContent {
  val byFile = applied.associateBy { it.fileIndex }
  val newRows = ArrayList<DiffRow>(rows.size)
  val headers = ArrayList<Int>(fileHeaderRowIndex.size)
  val newFiles = files.toMutableList()
  val newHighlights = highlights.toMutableList()
  val entries = ArrayList<HighlightEntry>()
  for (fileIndex in fileHeaderRowIndex.indices) {
    val start = fileHeaderRowIndex[fileIndex]
    val end = fileHeaderRowIndex.getOrNull(fileIndex + 1) ?: rows.size
    headers += newRows.size
    val expansion = byFile[fileIndex]
    if (expansion == null) {
      newRows.addAll(rows.subList(start, end))
      continue
    }
    newRows.addAll(flattenExpanded(listOf(expansion.expanded), fileIndex, largeDiffLines))
    newFiles[fileIndex] = expansion.expanded.file
    if (fileIndex in newHighlights.indices) {
      val slot = HighlightSlot()
      newHighlights[fileIndex] = slot
      entries += HighlightEntry(fileIndex, expansion.expanded.file, slot)
    }
  }
  return ExpandedContent(applied, newFiles, newRows, headers, splitRowsOf(newRows), newHighlights, entries)
}

internal fun ViewerUiState.withContent(content: ExpandedContent): ViewerUiState = copy(
  files = content.files,
  rows = content.rows,
  fileHeaderRowIndex = content.headers,
  split = content.split,
  highlights = content.highlights,
  expansions = expansions + content.applied.map { it.entry to it.expansion },
  expansionErrors = expansionErrors - content.applied.map { it.entry }.toSet(),
  contentId = contentId + 1,
  rowsGeneration = rowsGeneration + 1,
)

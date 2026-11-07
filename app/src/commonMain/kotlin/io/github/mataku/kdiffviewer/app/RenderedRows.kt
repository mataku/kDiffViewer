package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.app.highlight.LanguageId
import io.github.mataku.kdiffviewer.app.highlight.languageIdFor
import io.github.mataku.kdiffviewer.app.markdown.SourceBlock
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind

@Immutable
data class HunkLineRange(val hunkIndex: Int, val fromLineIndex: Int, val toLineIndex: Int)

fun FileDiff.isRenderable(): Boolean {
  val path = newPath ?: return false
  val hunks = (content as? DiffContent.Text)?.hunks ?: return false
  return hunks.isNotEmpty() && languageIdFor(path) == LanguageId.Markdown
}

fun renderedBodyRows(fileIndex: Int, file: FileDiff, blocks: List<SourceBlock>): List<DiffRow> {
  val hunks = (file.content as? DiffContent.Text)?.hunks.orEmpty()
  val newLines = NewSideLines(hunks)
  val deletions = pureDeletions(hunks)
  val rows = ArrayList<DiffRow>()
  var folded = 0
  var nextDeletion = 0

  fun flushFold() {
    if (folded > 0) rows += DiffRow.UnchangedFold(fileIndex, rows.size, folded)
    folded = 0
  }

  fun addDeletion(deletion: PureDeletion) {
    rows += DiffRow.RemovedMarker(fileIndex, rows.size, deletion.range, deletion.oldStart, deletion.oldEnd, deletion.count)
  }

  for (block in blocks) {
    while (nextDeletion < deletions.size && deletions[nextDeletion].newPosition <= block.startLine) {
      flushFold()
      addDeletion(deletions[nextDeletion++])
    }
    var inside = nextDeletion
    while (inside < deletions.size && deletions[inside].newPosition <= block.endLine) inside++
    val hasDeletion = inside > nextDeletion
    val commentRange = newLines.commentRange(block.startLine, block.endLine)
    if (!hasDeletion && !commentRange.changed) {
      folded++
      continue
    }
    flushFold()
    rows += DiffRow.RenderedBlock(fileIndex, rows.size, block, commentRange.range)
    while (nextDeletion < inside) addDeletion(deletions[nextDeletion++])
  }
  flushFold()
  while (nextDeletion < deletions.size) addDeletion(deletions[nextDeletion++])
  return rows
}

private class BlockLines(val range: HunkLineRange?, val changed: Boolean)

private class NewSideLines(hunks: List<Hunk>) {
  private val numbers = ArrayList<Int>()
  private val hunkIndices = ArrayList<Int>()
  private val lineIndices = ArrayList<Int>()
  private val added = ArrayList<Boolean>()

  init {
    hunks.forEachIndexed { hunkIndex, hunk ->
      hunk.lines.forEachIndexed { lineIndex, line ->
        val number = line.newLineNo
        if (number != null && line.kind != LineKind.NoNewlineAtEof) {
          numbers += number
          hunkIndices += hunkIndex
          lineIndices += lineIndex
          added += line.kind == LineKind.Added
        }
      }
    }
  }

  fun commentRange(startLine: Int, endLine: Int): BlockLines {
    val search = numbers.binarySearch(startLine)
    val first = if (search >= 0) search else -search - 1
    var last = first
    var firstAdded = -1
    while (last < numbers.size && numbers[last] <= endLine) {
      if (firstAdded < 0 && added[last]) firstAdded = last
      last++
    }
    if (first == last) return BlockLines(null, changed = false)
    val hunkIndex = hunkIndices[if (firstAdded >= 0) firstAdded else first]
    var from = -1
    var to = -1
    for (index in first until last) {
      if (hunkIndices[index] != hunkIndex) continue
      if (from < 0) from = lineIndices[index]
      to = lineIndices[index]
    }
    return BlockLines(HunkLineRange(hunkIndex, from, to), changed = firstAdded >= 0)
  }
}

private class PureDeletion(val range: HunkLineRange, val oldStart: Int, val oldEnd: Int, val count: Int, val newPosition: Int)

private fun pureDeletions(hunks: List<Hunk>): List<PureDeletion> {
  val deletions = ArrayList<PureDeletion>()
  hunks.forEachIndexed { hunkIndex, hunk ->
    val lines = hunk.lines
    var index = 0
    var lastNew = if (hunk.newCount == 0) hunk.newStart else hunk.newStart - 1
    while (index < lines.size) {
      val line = lines[index]
      if (line.kind != LineKind.Removed) {
        line.newLineNo?.let { lastNew = it }
        index++
        continue
      }
      val from = index
      var to = index
      var count = 0
      var scan = index
      while (scan < lines.size && (lines[scan].kind == LineKind.Removed || lines[scan].kind == LineKind.NoNewlineAtEof)) {
        if (lines[scan].kind == LineKind.Removed) {
          to = scan
          count++
        }
        scan++
      }
      if (!lines.isAdded(from - 1) && !lines.isAdded(scan)) {
        val oldStart = lines[from].oldLineNo ?: 0
        val oldEnd = lines[to].oldLineNo ?: oldStart
        deletions += PureDeletion(HunkLineRange(hunkIndex, from, to), oldStart, oldEnd, count, lastNew + 1)
      }
      index = scan
    }
  }
  return deletions
}

private fun List<DiffLine>.isAdded(index: Int): Boolean = getOrNull(index)?.kind == LineKind.Added

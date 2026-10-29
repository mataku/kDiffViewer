package io.github.mataku.kdiffviewer.core.review

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val FileDiff.anchorPath: String
  get() = if (change == ChangeType.Deleted) oldPath!! else newPath!!

fun anchorOf(file: FileDiff, hunkIndex: Int, lineIndex: Int, side: Side? = null): Anchor.Lines? {
  val hunks = (file.content as? DiffContent.Text)?.hunks ?: return null
  val line = hunks.getOrNull(hunkIndex)?.lines?.getOrNull(lineIndex) ?: return null
  val resolvedSide = when (line.kind) {
    LineKind.NoNewlineAtEof -> return null
    LineKind.Removed -> Side.Old
    LineKind.Added -> Side.New
    LineKind.Context -> side ?: Side.New
  }
  if (side != null && side != resolvedSide) return null
  val lineNo = (if (resolvedSide == Side.Old) line.oldLineNo else line.newLineNo) ?: return null
  return Anchor.Lines(file.anchorPath, resolvedSide, lineNo, lineNo, listOf(line.text))
}

data class RangeAnchor(val anchor: Anchor.Lines, val location: LineLocation)

fun anchorOfRange(
  file: FileDiff,
  fileIndex: Int,
  hunkIndex: Int,
  fromLineIndex: Int,
  toLineIndex: Int,
  side: Side? = null,
): RangeAnchor? {
  val hunks = (file.content as? DiffContent.Text)?.hunks ?: return null
  val lines = hunks.getOrNull(hunkIndex)?.lines ?: return null
  if (lines.isEmpty()) return null
  val from = fromLineIndex.coerceIn(0, lines.lastIndex)
  val to = toLineIndex.coerceIn(0, lines.lastIndex)
  if (lines[from].kind == LineKind.NoNewlineAtEof) return null
  val resolvedSide = side ?: when (lines[from].kind) {
    LineKind.Removed -> Side.Old
    LineKind.Added -> Side.New
    else -> sideTowardTo(lines, from, to)
  }
  val snapshot = ArrayList<String>()
  var startLine = 0
  var endLine = 0
  var lastIndex = -1
  for (index in min(from, to)..max(from, to)) {
    val line = lines[index]
    val lineNo = line.numberOn(resolvedSide) ?: continue
    if (lastIndex < 0) startLine = lineNo
    endLine = lineNo
    lastIndex = index
    snapshot.add(line.text)
  }
  if (lastIndex < 0) return null
  return RangeAnchor(
    Anchor.Lines(file.anchorPath, resolvedSide, startLine, endLine, snapshot),
    LineLocation(fileIndex),
  )
}

private fun sideTowardTo(lines: List<DiffLine>, from: Int, to: Int): Side {
  val step = if (to >= from) 1 else -1
  var index = from
  while (index in lines.indices) {
    when (lines[index].kind) {
      LineKind.Removed -> return Side.Old
      LineKind.Added -> return Side.New
      else -> Unit
    }
    if (index == to) break
    index += step
  }
  return Side.New
}

private fun DiffLine.numberOn(side: Side): Int? = when (side) {
  Side.Old -> if (kind == LineKind.Removed || kind == LineKind.Context) oldLineNo else null
  Side.New -> if (kind == LineKind.Added || kind == LineKind.Context) newLineNo else null
}

data class HunkLine(val hunkIndex: Int, val lineIndex: Int)

fun FileDiff.locate(side: Side, line: Int): HunkLine? {
  val hunks = (content as? DiffContent.Text)?.hunks ?: return null
  hunks.forEachIndexed { hunkIndex, hunk ->
    hunk.lines.forEachIndexed { lineIndex, diffLine ->
      if (diffLine.numberOn(side) == line) return HunkLine(hunkIndex, lineIndex)
    }
  }
  return null
}

fun reanchor(anchor: Anchor, files: List<FileDiff>): Pair<Anchor, Placement> = resolve(anchor, files).let { it.anchor to it.placement }

fun reanchor(thread: ReviewThread, files: List<FileDiff>): Pair<ReviewThread, Placement> {
  val resolution = resolve(thread.anchor, files)
  return thread.copy(anchor = resolution.anchor, outdated = resolution.outdated) to resolution.placement
}

private class Resolution(val anchor: Anchor, val placement: Placement, val outdated: Boolean)

private class SideLine(val lineNo: Int, val text: String, val hunkIndex: Int, val lineIndex: Int)

private class Run(val fileIndex: Int, val first: Int, val last: SideLine)

private fun resolve(anchor: Anchor, files: List<FileDiff>): Resolution {
  val firstFile = files.indexOfFirst { it.anchorPath == anchor.path }
  return when (anchor) {
    is Anchor.File ->
      if (firstFile >= 0) {
        Resolution(anchor, Placement.OnFile(firstFile), outdated = false)
      } else {
        Resolution(anchor, Placement.Orphan, outdated = true)
      }

    is Anchor.Lines -> resolveLines(anchor, files, firstFile)
  }
}

private fun resolveLines(anchor: Anchor.Lines, files: List<FileDiff>, firstFile: Int): Resolution {
  var best: Run? = null
  var bestDistance = Int.MAX_VALUE
  files.forEachIndexed { fileIndex, file ->
    if (file.anchorPath != anchor.path) return@forEachIndexed
    val sideLines = sideLines(file, anchor.side)
    for (run in runs(fileIndex, sideLines, anchor.snapshot)) {
      val distance = abs(run.first - anchor.startLine)
      val current = best
      if (current == null || distance < bestDistance || (distance == bestDistance && run.first < current.first)) {
        best = run
        bestDistance = distance
      }
    }
  }
  val match = best
  if (match == null) {
    val placement = if (firstFile >= 0) Placement.OnFile(firstFile) else Placement.Orphan
    return Resolution(anchor, placement, outdated = true)
  }
  val resolvedAnchor = if (match.first == anchor.startLine) {
    anchor
  } else {
    anchor.copy(startLine = match.first, endLine = match.first + anchor.snapshot.size - 1)
  }
  val location = LineLocation(match.fileIndex)
  return Resolution(resolvedAnchor, Placement.OnLine(location), outdated = false)
}

private fun sideLines(file: FileDiff, side: Side): List<SideLine> {
  val hunks = (file.content as? DiffContent.Text)?.hunks ?: return emptyList()
  val result = ArrayList<SideLine>()
  hunks.forEachIndexed { hunkIndex, hunk ->
    hunk.lines.forEachIndexed { lineIndex, line ->
      val lineNo = line.numberOn(side)
      if (lineNo != null) result.add(SideLine(lineNo, line.text, hunkIndex, lineIndex))
    }
  }
  return result
}

private fun runs(fileIndex: Int, lines: List<SideLine>, snapshot: List<String>): Sequence<Run> {
  if (snapshot.isEmpty()) return emptySequence()
  return sequence {
    for (start in 0..lines.size - snapshot.size) {
      var matches = true
      for (offset in snapshot.indices) {
        val line = lines[start + offset]
        if (line.text != snapshot[offset] || line.lineNo != lines[start].lineNo + offset) {
          matches = false
          break
        }
      }
      if (matches) yield(Run(fileIndex, lines[start].lineNo, lines[start + snapshot.size - 1]))
    }
  }
}

package io.github.mataku.kdiffviewer.core.review

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.math.abs

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
  val location = LineLocation(match.fileIndex, match.last.hunkIndex, match.last.lineIndex)
  return Resolution(resolvedAnchor, Placement.OnLine(location), outdated = false)
}

private fun sideLines(file: FileDiff, side: Side): List<SideLine> {
  val hunks = (file.content as? DiffContent.Text)?.hunks ?: return emptyList()
  val result = ArrayList<SideLine>()
  hunks.forEachIndexed { hunkIndex, hunk ->
    hunk.lines.forEachIndexed { lineIndex, line ->
      val lineNo = when (side) {
        Side.Old -> if (line.kind == LineKind.Removed || line.kind == LineKind.Context) line.oldLineNo else null
        Side.New -> if (line.kind == LineKind.Added || line.kind == LineKind.Context) line.newLineNo else null
      }
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

package io.github.mataku.kdiffviewer.core.model

const val EXPAND_STEP = 20

private const val CONTEXT_LINES = 3

enum class ExpandDirection { Up, Down, All }

data class ContextExpansion(val revealed: List<IntRange>, val newLineCount: Int?)

data class GapView(val gapIndex: Int, val hidden: Int?, val canUp: Boolean, val canDown: Boolean)

class ExpandedFile(val file: FileDiff, val gapBefore: List<GapView?>, val trailingGap: GapView?)

private val Hunk.firstNew: Int get() = if (newCount > 0) newStart else newStart + 1

private val Hunk.lastNew: Int get() = firstNew + newCount - 1

private val Hunk.firstOld: Int get() = if (oldCount > 0) oldStart else oldStart + 1

private val FileDiff.hunks: List<Hunk> get() = (content as? DiffContent.Text)?.hunks.orEmpty()

fun FileDiff.hasTrailingGap(): Boolean {
  val lines = hunks.lastOrNull()?.lines ?: return false
  var trailingContext = 0
  for (index in lines.indices.reversed()) {
    when (lines[index].kind) {
      LineKind.Context -> trailingContext++
      LineKind.NoNewlineAtEof -> if (index > 0 && lines[index - 1].kind != LineKind.Removed) return false
      LineKind.Added, LineKind.Removed -> return trailingContext >= CONTEXT_LINES
    }
  }
  return false
}

fun gapsOf(file: FileDiff, newLineCount: Int?): List<IntRange?> {
  val hunks = file.hunks
  if (hunks.isEmpty()) return emptyList()
  val gaps = ArrayList<IntRange?>(hunks.size + 1)
  hunks.forEachIndexed { index, hunk ->
    val start = if (index == 0) 1 else hunks[index - 1].lastNew + 1
    gaps += start until hunk.firstNew
  }
  gaps += if (file.hasTrailingGap() && newLineCount != null) (hunks.last().lastNew + 1)..newLineCount else null
  return gaps
}

fun ContextExpansion?.reveal(file: FileDiff, gapIndex: Int, direction: ExpandDirection, newLineCount: Int): ContextExpansion {
  val current = this?.revealed.orEmpty()
  val gaps = gapsOf(file, newLineCount)
  val gap = gaps.getOrNull(gapIndex)
  if (gap == null || gap.isEmpty()) return ContextExpansion(current, newLineCount)
  val top = if (gapIndex == 0) 0 else coveredFrom(current, gap.first, 1, gap)
  val bottom = if (gapIndex == gaps.lastIndex) 0 else coveredFrom(current, gap.last, -1, gap)
  val hiddenStart = gap.first + top
  val hiddenEnd = gap.last - bottom
  if (hiddenStart > hiddenEnd) return ContextExpansion(current, newLineCount)
  val effective = when {
    gapIndex == 0 && direction == ExpandDirection.Down -> ExpandDirection.Up
    gapIndex == gaps.lastIndex && direction == ExpandDirection.Up -> ExpandDirection.Down
    else -> direction
  }
  val added = when (effective) {
    ExpandDirection.Down -> hiddenStart..minOf(hiddenStart + EXPAND_STEP - 1, hiddenEnd)
    ExpandDirection.Up -> maxOf(hiddenEnd - EXPAND_STEP + 1, hiddenStart)..hiddenEnd
    ExpandDirection.All -> hiddenStart..hiddenEnd
  }
  return ContextExpansion(normalized(current + listOf(added)), newLineCount)
}

private fun coveredFrom(ranges: List<IntRange>, from: Int, step: Int, gap: IntRange): Int {
  var line = from
  var count = 0
  while (line in gap && ranges.any { line in it }) {
    count++
    line += step
  }
  return count
}

private fun normalized(ranges: List<IntRange>): List<IntRange> {
  val sorted = ranges.filter { !it.isEmpty() }.sortedBy { it.first }
  val result = ArrayList<IntRange>()
  for (range in sorted) {
    val last = result.lastOrNull()
    if (last != null && range.first <= last.last + 1) {
      result[result.lastIndex] = last.first..maxOf(last.last, range.last)
    } else {
      result += range
    }
  }
  return result
}

fun expandContext(file: FileDiff, expansion: ContextExpansion?, newLines: List<String>?): ExpandedFile {
  val hunks = file.hunks
  if (hunks.isEmpty()) return ExpandedFile(file, emptyList(), null)
  val gaps = gapsOf(file, expansion?.newLineCount)
  val revealed = if (newLines == null) emptyList() else expansion?.revealed.orEmpty()
  val trailingIndex = hunks.size
  val output = ArrayList<Hunk>()
  val gapBefore = ArrayList<GapView?>()
  var builder: HunkBuilder? = null
  var delta = hunks[0].firstOld - hunks[0].firstNew
  var changed = false
  var trailingGap: GapView? = null

  fun reveal(range: IntRange, target: HunkBuilder) {
    for (line in range) {
      val text = newLines?.getOrNull(line - 1) ?: continue
      target.lines += DiffLine(LineKind.Context, text, line + delta, line)
      changed = true
    }
  }

  for (gapIndex in 0..trailingIndex) {
    val gap = gaps[gapIndex]
    val known = gap ?: IntRange.EMPTY
    val top = if (gapIndex == 0) 0 else coveredFrom(revealed, known.first, 1, known)
    val bottom = if (gapIndex == trailingIndex) 0 else coveredFrom(revealed, known.last, -1, known)
    val hidden = if (gap == null) null else (known.count() - top - bottom).coerceAtLeast(0)
    if (gapIndex == trailingIndex) {
      if (top > 0) reveal(known.first until known.first + top, builder!!)
      if (file.hasTrailingGap() && (hidden == null || hidden > 0)) trailingGap = GapView(gapIndex, hidden, canUp = false, canDown = true)
      break
    }
    val hunk = hunks[gapIndex]
    val current = builder
    val next = if (current != null && !known.isEmpty() && hidden == 0) {
      reveal(known, current)
      current
    } else {
      if (current != null && top > 0) reveal(known.first until known.first + top, current)
      current?.let { output += it.build() }
      gapBefore += if (hidden != null && hidden > 0) GapView(gapIndex, hidden, canUp = true, canDown = gapIndex > 0) else null
      HunkBuilder(hunk).also { if (bottom > 0) reveal((known.last - bottom + 1)..known.last, it) }
    }
    next.lines += hunk.lines
    builder = next
    delta += hunk.oldCount - hunk.newCount
  }
  builder?.let { output += it.build() }
  if (!changed) return ExpandedFile(file, gapBefore, trailingGap)
  return ExpandedFile(file.copy(content = DiffContent.Text(output)), gapBefore, trailingGap)
}

private class HunkBuilder(private val first: Hunk) {
  val lines = ArrayList<DiffLine>()

  fun build(): Hunk {
    var oldCount = 0
    var newCount = 0
    var oldStart: Int? = null
    var newStart: Int? = null
    for (line in lines) {
      line.oldLineNo?.let {
        if (oldStart == null) oldStart = it
        oldCount++
      }
      line.newLineNo?.let {
        if (newStart == null) newStart = it
        newCount++
      }
    }
    return Hunk(oldStart ?: first.oldStart, oldCount, newStart ?: first.newStart, newCount, first.header, lines)
  }
}

fun contextMatches(file: FileDiff, newLines: List<String>): Boolean {
  for (hunk in file.hunks) {
    for (line in hunk.lines) {
      if (line.kind != LineKind.Context && line.kind != LineKind.Added) continue
      val number = line.newLineNo ?: continue
      if (newLines.getOrNull(number - 1) != line.text) return false
    }
  }
  return true
}

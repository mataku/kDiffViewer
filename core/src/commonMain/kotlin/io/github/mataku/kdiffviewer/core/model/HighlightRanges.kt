package io.github.mataku.kdiffviewer.core.model

enum class Side { Old, New }

fun FileDiff.highlightRanges(side: Side): List<IntRange> {
  val hunks = (content as? DiffContent.Text)?.hunks ?: return emptyList()
  val ranges = ArrayList<IntRange>()
  var start = -1
  var last = -1
  for (hunk in hunks) {
    for (line in hunk.lines) {
      val number = when (side) {
        Side.Old -> if (line.kind == LineKind.Removed) line.oldLineNo else null
        Side.New -> if (line.kind == LineKind.Added || line.kind == LineKind.Context) line.newLineNo else null
      } ?: continue
      if (start != -1 && number == last + 1) {
        last = number
      } else {
        if (start != -1) ranges += start..last
        start = number
        last = number
      }
    }
  }
  if (start != -1) ranges += start..last
  return ranges
}

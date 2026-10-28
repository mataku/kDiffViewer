package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.DiffStats
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.stats

const val LARGE_DIFF_LINES = 1_000

@Immutable
sealed interface DiffRow {
  val key: String

  @Immutable
  data class FileHeader(val fileIndex: Int, val file: FileDiff, val stats: DiffStats, val largeDiff: Boolean = false) : DiffRow {
    override val key: String get() = "f$fileIndex"
  }

  @Immutable
  data class HunkHeader(val fileIndex: Int, val hunkIndex: Int, val hunk: Hunk) : DiffRow {
    override val key: String get() = "h$fileIndex-$hunkIndex"
  }

  @Immutable
  data class Line(val fileIndex: Int, val hunkIndex: Int, val lineIndex: Int, val line: DiffLine) : DiffRow {
    override val key: String get() = "l$fileIndex-$hunkIndex-$lineIndex"
  }

  @Immutable
  data class Placeholder(val fileIndex: Int, val message: String) : DiffRow {
    override val key: String get() = "p$fileIndex"
  }
}

fun flattenRows(files: List<FileDiff>, startIndex: Int = 0, largeDiffLines: Int = LARGE_DIFF_LINES): List<DiffRow> {
  val rows = ArrayList<DiffRow>()
  files.forEachIndexed { position, file ->
    val fileIndex = startIndex + position
    val stats = file.stats
    rows += DiffRow.FileHeader(fileIndex, file, stats, largeDiff = stats.additions + stats.deletions >= largeDiffLines)
    when (val content = file.content) {
      is DiffContent.Text -> content.hunks.forEachIndexed { hunkIndex, hunk ->
        rows += DiffRow.HunkHeader(fileIndex, hunkIndex, hunk)
        hunk.lines.forEachIndexed { lineIndex, line ->
          rows += DiffRow.Line(fileIndex, hunkIndex, lineIndex, line)
        }
      }

      DiffContent.Binary -> rows += DiffRow.Placeholder(fileIndex, "Binary file not shown")

      DiffContent.TooLarge -> rows += DiffRow.Placeholder(fileIndex, "File too large to display")

      DiffContent.Unparseable -> rows += DiffRow.Placeholder(fileIndex, "Could not parse this diff")
    }
  }
  return rows
}

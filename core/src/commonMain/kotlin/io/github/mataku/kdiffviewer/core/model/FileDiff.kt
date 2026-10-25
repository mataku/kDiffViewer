package io.github.mataku.kdiffviewer.core.model

enum class ChangeType { Added, Deleted, Modified, Renamed }

enum class LineKind { Context, Added, Removed, NoNewlineAtEof }

data class DiffLine(
  val kind: LineKind,
  val text: String,
  val oldLineNo: Int?,
  val newLineNo: Int?,
)

data class Hunk(
  val oldStart: Int,
  val oldCount: Int,
  val newStart: Int,
  val newCount: Int,
  val header: String,
  val lines: List<DiffLine>,
)

sealed interface DiffContent {
  data class Text(val hunks: List<Hunk>) : DiffContent
  data object Binary : DiffContent
  data object TooLarge : DiffContent
  data object Unparseable : DiffContent
}

data class FileDiff(
  val oldPath: String?,
  val newPath: String?,
  val change: ChangeType,
  val isUntracked: Boolean,
  val content: DiffContent,
)

val FileDiff.displayPath: String
  get() = newPath ?: oldPath!!

data class DiffStats(val additions: Int, val deletions: Int)

val FileDiff.stats: DiffStats
  get() {
    val hunks = (content as? DiffContent.Text)?.hunks ?: return DiffStats(0, 0)
    var additions = 0
    var deletions = 0
    for (hunk in hunks) {
      for (line in hunk.lines) {
        when (line.kind) {
          LineKind.Added -> additions++
          LineKind.Removed -> deletions++
          else -> Unit
        }
      }
    }
    return DiffStats(additions, deletions)
  }

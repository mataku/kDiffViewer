package io.github.mataku.kdiffviewer.core.review

import kotlin.time.Instant

enum class Side { Old, New }

sealed interface Anchor {
  val path: String

  data class File(override val path: String) : Anchor

  data class Lines(
    override val path: String,
    val side: Side,
    val startLine: Int,
    val endLine: Int,
    val snapshot: List<String>,
  ) : Anchor
}

sealed interface Author {
  data object User : Author

  data class Agent(val name: String) : Author
}

data class Comment(
  val id: String,
  val author: Author,
  val body: String,
  val createdAt: Instant,
)

data class ReviewThread(
  val id: String,
  val anchor: Anchor,
  val comments: List<Comment>,
  val resolved: Boolean,
  val outdated: Boolean,
)

data class LineLocation(val fileIndex: Int, val hunkIndex: Int, val lineIndex: Int)

sealed interface Placement {
  data class OnLine(val location: LineLocation) : Placement

  data class OnFile(val fileIndex: Int) : Placement

  data object Orphan : Placement
}

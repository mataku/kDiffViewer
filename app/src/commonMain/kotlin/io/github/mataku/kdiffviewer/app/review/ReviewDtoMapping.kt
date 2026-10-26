package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.Side
import io.github.mataku.kdiffviewer.protocol.AuthorDto
import io.github.mataku.kdiffviewer.protocol.CommentDto
import io.github.mataku.kdiffviewer.protocol.ThreadDto

fun Comment.toDto(): CommentDto = CommentDto(
  id = id,
  author = when (val a = author) {
    Author.User -> AuthorDto("user")
    is Author.Agent -> AuthorDto("agent", a.name)
  },
  body = body,
  createdAt = createdAt.toString(),
)

fun ReviewThread.toDto(): ThreadDto {
  val a = anchor
  return when (a) {
    is Anchor.Lines -> ThreadDto(
      id = id,
      path = a.path,
      side = if (a.side == Side.Old) "old" else "new",
      startLine = a.startLine,
      endLine = a.endLine,
      snapshot = a.snapshot,
      resolved = resolved,
      outdated = outdated,
      comments = comments.map { it.toDto() },
    )

    is Anchor.File -> ThreadDto(
      id = id,
      path = a.path,
      snapshot = emptyList(),
      resolved = resolved,
      outdated = outdated,
      comments = comments.map { it.toDto() },
    )
  }
}

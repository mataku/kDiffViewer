package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.CommentDto
import io.github.mataku.kdiffviewer.protocol.ThreadDto

fun formatThreads(threads: List<ThreadDto>, all: Boolean): String {
  if (threads.isEmpty()) return if (all) "No threads." else "No open threads."
  return threads.joinToString("\n\n") { formatThread(it) }
}

private fun formatThread(thread: ThreadDto): String {
  val lines = mutableListOf<String>()
  val location = thread.startLine?.let { start ->
    val end = thread.endLine
    val range = if (end != null && end != start) "$start-$end" else "$start"
    "${thread.path}:$range (${thread.side ?: "new"})"
  } ?: "${thread.path} (file)"
  val markers = (if (thread.resolved) " [resolved]" else "") + (if (thread.outdated) " [outdated]" else "")
  lines += "${thread.id}  $location$markers"
  thread.snapshot.forEach { lines += "    │ $it" }
  thread.comments.forEach { lines += formatComment(it) }
  return lines.joinToString("\n")
}

private fun formatComment(comment: CommentDto): String {
  val name = if (comment.author.kind == "user") "You" else comment.author.name ?: "agent"
  val body = comment.body.split("\n")
  return (listOf("  $name: ${body.first()}") + body.drop(1).map { "    $it" }).joinToString("\n")
}

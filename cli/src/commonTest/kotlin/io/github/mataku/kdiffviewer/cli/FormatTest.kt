package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.AuthorDto
import io.github.mataku.kdiffviewer.protocol.CommentDto
import io.github.mataku.kdiffviewer.protocol.ThreadDto
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
  private fun comment(id: String, author: AuthorDto, body: String) = CommentDto(id, author, body, "2026-10-26T00:00:00Z")

  @Test
  fun lineThreadWithTwoComments() {
    val thread = ThreadDto(
      id = "t1",
      path = "src/A.kt",
      side = "new",
      startLine = 10,
      endLine = 12,
      snapshot = listOf("val a = 1", "val b = 2"),
      resolved = false,
      outdated = false,
      comments = listOf(
        comment("c1", AuthorDto("user"), "first\nsecond"),
        comment("c2", AuthorDto("agent", "claude"), "done"),
      ),
    )
    val expected = listOf(
      "t1  src/A.kt:10-12 (new)",
      "    │ val a = 1",
      "    │ val b = 2",
      "  You: first",
      "    second",
      "  claude: done",
    ).joinToString("\n")
    assertEquals(expected, formatThreads(listOf(thread), all = false))
  }

  @Test
  fun singleLineHasNoRangeSuffix() {
    val thread = ThreadDto("t2", "B.kt", "old", 5, 5, listOf("x"), false, false, listOf(comment("c1", AuthorDto("user"), "hi")))
    assertEquals("t2  B.kt:5 (old)\n    │ x\n  You: hi", formatThreads(listOf(thread), all = false))
    val noEnd = thread.copy(endLine = null)
    assertEquals("t2  B.kt:5 (old)\n    │ x\n  You: hi", formatThreads(listOf(noEnd), all = false))
  }

  @Test
  fun fileThread() {
    val thread = ThreadDto("t3", "docs/R.md", null, null, null, emptyList(), false, false, listOf(comment("c1", AuthorDto("user"), "why")))
    assertEquals("t3  docs/R.md (file)\n  You: why", formatThreads(listOf(thread), all = false))
  }

  @Test
  fun resolvedAndOutdatedMarkers() {
    val thread = ThreadDto("t4", "C.kt", "new", 1, 1, listOf("l"), true, true, listOf(comment("c1", AuthorDto("agent", null), "ok")))
    assertEquals("t4  C.kt:1 (new) [resolved] [outdated]\n    │ l\n  agent: ok", formatThreads(listOf(thread), all = true))
    assertEquals("t4  C.kt:1 (new) [outdated]\n    │ l\n  agent: ok", formatThreads(listOf(thread.copy(resolved = false)), all = true))
    assertEquals("t4  C.kt:1 (new) [resolved]\n    │ l\n  agent: ok", formatThreads(listOf(thread.copy(outdated = false)), all = true))
  }

  @Test
  fun threadsAreSeparatedByBlankLine() {
    val a = ThreadDto("t1", "A", null, null, null, emptyList(), false, false, listOf(comment("c", AuthorDto("user"), "a")))
    val b = a.copy(id = "t2")
    assertEquals("t1  A (file)\n  You: a\n\nt2  A (file)\n  You: a", formatThreads(listOf(a, b), all = false))
  }

  @Test
  fun emptyLists() {
    assertEquals("No open threads.", formatThreads(emptyList(), all = false))
    assertEquals("No threads.", formatThreads(emptyList(), all = true))
  }
}

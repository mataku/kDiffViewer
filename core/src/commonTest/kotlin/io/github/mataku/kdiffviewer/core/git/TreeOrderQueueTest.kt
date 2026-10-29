package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.TreePathOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class TreeOrderQueueTest {
  private fun streamed(gitOrder: List<String>, known: List<String> = emptyList()): List<List<String>> {
    val queue = TreeOrderQueue<String>()
    known.forEach { queue.add(it, it) }
    val batches = gitOrder.map { path ->
      queue.add(path, path)
      queue.drainReady(path)
    }
    return batches + listOf(queue.drainAll())
  }

  @Test
  fun aDirectoryIsReleasedOnceTheStreamLeavesIt() {
    val batches = streamed(listOf("README.md", "app/a.kt", "app/b.kt", "lib/c.kt", "m.txt"))
    assertEquals(
      listOf(emptyList(), emptyList(), emptyList(), listOf("app/a.kt", "app/b.kt"), listOf("lib/c.kt"), listOf("README.md", "m.txt")),
      batches,
    )
  }

  @Test
  fun filesInADirectoryWaitForItsSubdirectories() {
    val batches = streamed(listOf("app/a.kt", "app/src/b.kt", "app/z.kt", "zz.txt"))
    assertEquals(listOf("app/src/b.kt", "app/a.kt", "app/z.kt"), batches[2] + batches[3])
    assertEquals(emptyList(), batches[0] + batches[1])
  }

  @Test
  fun knownEntriesAreMergedIntoTreeOrder() {
    val all = streamed(listOf("README.md", "app/x.kt", "m.txt"), known = listOf("app/u.txt", "z.txt")).flatten()
    assertEquals(listOf("app/u.txt", "app/x.kt", "README.md", "m.txt", "z.txt"), all)
  }

  @Test
  fun everyEntryIsReleasedExactlyOnceInTreeOrderForGitOrderedInput() {
    val paths = listOf("README.md", "a-b.txt", "a/renamed.txt", "app/src/Main.kt", "app/src/main/x.kt", "app/x.kt", "b/y.kt", "m.txt", "z/z/z.kt")
    val known = listOf("app/u.txt", "b.txt", "app/src/v.kt")
    assertEquals((paths + known).sortedWith(TreePathOrder), streamed(paths, known).flatten())
  }

  @Test
  fun duplicatePathsKeepArrivalOrder() {
    val queue = TreeOrderQueue<Int>()
    queue.add("a.kt", 1)
    queue.add("a.kt", 2)
    assertEquals(listOf(1, 2), queue.drainAll())
  }
}

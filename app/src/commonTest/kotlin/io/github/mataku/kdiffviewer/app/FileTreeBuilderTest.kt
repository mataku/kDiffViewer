package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.TreePathOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FileTreeBuilderTest {
  private fun file(path: String) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(emptyList()))

  private val paths = listOf(
    "b.txt",
    "src/z.kt",
    "src/a.kt",
    "README.md",
    "a.txt",
    "a.txt",
    "a/b/c.kt",
    "a/b/d/e.kt",
    "a/f.kt",
    "src/a.kt",
    "src/main/Main.kt",
  )

  @Test
  fun filesInTreePathOrderAppearInIndexOrder() {
    val files = paths.sortedWith(TreePathOrder).map(::file)
    val indices = buildFileTree(files).mapNotNull { it.fileIndex }
    assertEquals(files.indices.toList(), indices)
  }

  @Test
  fun rowsMatchBuildFileTreeForAnyBatchSplit() {
    val files = paths.map(::file)
    for (batchSize in 1..files.size) {
      val builder = FileTreeBuilder()
      files.chunked(batchSize).forEach { batch ->
        builder.add(batch)
        builder.rowsIfDue()
      }
      assertEquals(buildFileTree(files), builder.rows(), "batchSize=$batchSize")
    }
  }

  @Test
  fun rowsMatchBuildFileTreeWithoutIntermediateFlattening() {
    val files = paths.map(::file)
    val builder = FileTreeBuilder()
    files.forEach { builder.add(listOf(it)) }
    assertEquals(buildFileTree(files), builder.rows())
  }

  @Test
  fun emptyBuilderHasNoRows() {
    assertEquals(emptyList(), FileTreeBuilder().rows())
  }

  @Test
  fun firstAddIsFlattenedImmediately() {
    val builder = FileTreeBuilder()
    builder.add(listOf(file("a.txt")))
    assertEquals(listOf(TreeRow(0, "a.txt", 0)), builder.rowsIfDue())
  }

  @Test
  fun flatteningWaitsUntilFileCountGrowsByAQuarter() {
    val files = (0 until 13).map { file("f${it.toString().padStart(2, '0')}.txt") }
    val builder = FileTreeBuilder()
    builder.add(files.take(8))
    val atEight = builder.rowsIfDue()
    assertEquals(8, atEight.size)
    builder.add(files.subList(8, 9))
    assertSame(atEight, builder.rowsIfDue())
    builder.add(files.subList(9, 10))
    assertEquals(10, builder.rowsIfDue().size)
  }

  @Test
  fun rowsAlwaysReflectEveryAddedFile() {
    val files = (0 until 13).map { file("f${it.toString().padStart(2, '0')}.txt") }
    val builder = FileTreeBuilder()
    builder.add(files.take(8))
    builder.rowsIfDue()
    builder.add(files.subList(8, 9))
    assertEquals(buildFileTree(files.take(9)), builder.rows())
  }

  @Test
  fun rowsDoesNotRebuildWhenNothingWasAdded() {
    val builder = FileTreeBuilder()
    builder.add(listOf(file("a.txt")))
    val first = builder.rows()
    assertSame(first, builder.rows())
    assertSame(first, builder.rowsIfDue())
  }

  @Test
  fun namesAreMarkedForHiddenControls() {
    val rows = buildFileTree(listOf(file("d\u202E/x\u0001.kt")))
    assertEquals(listOf(TreeRow(0, "d\uFFFD", null), TreeRow(1, "x\u2401.kt", 0)), rows)
  }
}

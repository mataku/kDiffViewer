package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import kotlin.test.Test
import kotlin.test.assertEquals

class FileTreeTest {
  private fun file(path: String) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(emptyList()))

  @Test
  fun treeGroupsDirectoriesFirstSorted() {
    val tree = buildFileTree(listOf(file("b.txt"), file("src/z.kt"), file("src/a.kt"), file("README.md")))
    assertEquals(
      listOf(
        TreeRow(0, "src", null),
        TreeRow(1, "a.kt", 2),
        TreeRow(1, "z.kt", 1),
        TreeRow(0, "README.md", 3),
        TreeRow(0, "b.txt", 0),
      ),
      tree,
    )
  }

  @Test
  fun treeKeepsDuplicatePaths() {
    val tree = buildFileTree(listOf(file("a.txt"), file("a.txt")))
    assertEquals(listOf(TreeRow(0, "a.txt", 0), TreeRow(0, "a.txt", 1)), tree)
  }

  @Test
  fun treeSharesIntermediateDirectoriesWithoutCompression() {
    val tree = buildFileTree(listOf(file("a/b/c.kt"), file("a/b/d/e.kt"), file("a/f.kt")))
    assertEquals(
      listOf(
        TreeRow(0, "a", null),
        TreeRow(1, "b", null),
        TreeRow(2, "d", null),
        TreeRow(3, "e.kt", 1),
        TreeRow(2, "c.kt", 0),
        TreeRow(1, "f.kt", 2),
      ),
      tree,
    )
  }
}

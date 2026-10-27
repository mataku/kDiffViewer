package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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

  private val nested = buildFileTree(listOf(file("a/b/c.kt"), file("a/f.kt"), file("g.kt")))

  @Test
  fun visibleTreeShowsEverythingWhenNothingIsCollapsed() {
    assertEquals(
      listOf(
        TreeEntry(TreeRow(0, "a", null), "a", false),
        TreeEntry(TreeRow(1, "b", null), "a/b", false),
        TreeEntry(TreeRow(2, "c.kt", 0), null, false),
        TreeEntry(TreeRow(1, "f.kt", 1), null, false),
        TreeEntry(TreeRow(0, "g.kt", 2), null, false),
      ),
      visibleTree(nested, emptySet()),
    )
  }

  @Test
  fun visibleTreeHidesDescendantsOfCollapsedDirectory() {
    assertEquals(
      listOf(
        TreeEntry(TreeRow(0, "a", null), "a", false),
        TreeEntry(TreeRow(1, "b", null), "a/b", true),
        TreeEntry(TreeRow(1, "f.kt", 1), null, false),
        TreeEntry(TreeRow(0, "g.kt", 2), null, false),
      ),
      visibleTree(nested, setOf("a/b")),
    )
  }

  @Test
  fun collapsedAncestorHidesNestedDirectories() {
    assertEquals(
      listOf(
        TreeEntry(TreeRow(0, "a", null), "a", true),
        TreeEntry(TreeRow(0, "g.kt", 2), null, false),
      ),
      visibleTree(nested, setOf("a", "a/b")),
    )
  }

  private val filterFiles = listOf(file("src/a.kt"), file("src/b.kt"), file("docs/ドキュメント.md"), file("README.md"), file("lib/x/deep.kt"))
  private val filterRows = buildFileTree(filterFiles)

  @Test
  fun filterKeepsMatchingFilesWithAncestorsAndDropsEmptyDirectories() {
    assertEquals(
      listOf(TreeRow(0, "src", null), TreeRow(1, "b.kt", 1)),
      filterTree(filterRows, filterFiles, "b.kt"),
    )
  }

  @Test
  fun directoryNameInQueryKeepsWholeSubtree() {
    assertEquals(
      listOf(TreeRow(0, "src", null), TreeRow(1, "a.kt", 0), TreeRow(1, "b.kt", 1)),
      filterTree(filterRows, filterFiles, "src"),
    )
  }

  @Test
  fun queryMayContainPathSeparators() {
    assertEquals(listOf(TreeRow(0, "lib", null), TreeRow(1, "x", null), TreeRow(2, "deep.kt", 4)), filterTree(filterRows, filterFiles, "x/deep"))
  }

  @Test
  fun matchIsCaseInsensitiveAndHandlesNonAscii() {
    assertEquals(listOf(TreeRow(0, "README.md", 3)), filterTree(filterRows, filterFiles, "readme"))
    assertEquals(listOf(TreeRow(0, "docs", null), TreeRow(1, "ドキュメント.md", 2)), filterTree(filterRows, filterFiles, "ドキュ"))
  }

  @Test
  fun queryIsTrimmed() {
    assertEquals(listOf(TreeRow(0, "README.md", 3)), filterTree(filterRows, filterFiles, "  README  "))
  }

  @Test
  fun whitespaceQueryReturnsTheInputInstance() {
    assertSame(filterRows, filterTree(filterRows, filterFiles, ""))
    assertSame(filterRows, filterTree(filterRows, filterFiles, " \t"))
  }

  @Test
  fun noMatchReturnsEmptyList() {
    assertEquals(emptyList(), filterTree(filterRows, filterFiles, "zzz"))
  }

  @Test
  fun duplicatePathsAreBothKept() {
    val files = listOf(file("a.txt"), file("a.txt"))
    assertEquals(listOf(TreeRow(0, "a.txt", 0), TreeRow(0, "a.txt", 1)), filterTree(buildFileTree(files), files, "a"))
  }

  @Test
  fun siblingAfterDeeperDirectoryKeepsOnlyItsOwnAncestors() {
    val files = listOf(file("a/b/c.kt"), file("a/f.kt"))
    assertEquals(listOf(TreeRow(0, "a", null), TreeRow(1, "f.kt", 1)), filterTree(buildFileTree(files), files, "f.kt"))
  }

  @Test
  fun filteredRowsRoundTripThroughVisibleTree() {
    val filtered = filterTree(filterRows, filterFiles, "deep")
    assertEquals(
      listOf(
        TreeEntry(TreeRow(0, "lib", null), "lib", false),
        TreeEntry(TreeRow(1, "x", null), "lib/x", false),
        TreeEntry(TreeRow(2, "deep.kt", 4), null, false),
      ),
      visibleTree(filtered, emptySet()),
    )
  }
}

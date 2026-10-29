package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
        TreeEntry(TreeRow(0, "a", null), DirectoryPath(null, "a"), false),
        TreeEntry(TreeRow(1, "b", null), DirectoryPath(null, "a/b"), false),
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
        TreeEntry(TreeRow(0, "a", null), DirectoryPath(null, "a"), false),
        TreeEntry(TreeRow(1, "b", null), DirectoryPath(null, "a/b"), true),
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
        TreeEntry(TreeRow(0, "a", null), DirectoryPath(null, "a"), true),
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
        TreeEntry(TreeRow(0, "lib", null), DirectoryPath(null, "lib"), false),
        TreeEntry(TreeRow(1, "x", null), DirectoryPath(null, "lib/x"), false),
        TreeEntry(TreeRow(2, "deep.kt", 4), null, false),
      ),
      visibleTree(filtered, emptySet()),
    )
  }

  @Test
  fun compactJoinsSingleChildDirectoryChain() {
    val tree = buildFileTree(listOf(file("a/b/c/d/e.kt")))
    assertEquals(listOf(TreeRow(0, "a/b/c/d", null), TreeRow(1, "e.kt", 0)), compactTree(tree))
  }

  @Test
  fun compactStopsWhereTheChainBranches() {
    val tree = buildFileTree(listOf(file("a/b/x.kt"), file("a/b/c/y.kt"), file("z.kt")))
    assertEquals(
      listOf(
        TreeRow(0, "a/b", null),
        TreeRow(1, "c", null),
        TreeRow(2, "y.kt", 1),
        TreeRow(1, "x.kt", 0),
        TreeRow(0, "z.kt", 2),
      ),
      compactTree(tree),
    )
  }

  @Test
  fun compactKeepsDirectoryWithFilesAndSiblingDirectories() {
    val tree = buildFileTree(listOf(file("a/b/c/d.kt"), file("a/e/f.kt"), file("a/g.kt")))
    assertEquals(
      listOf(
        TreeRow(0, "a", null),
        TreeRow(1, "b/c", null),
        TreeRow(2, "d.kt", 0),
        TreeRow(1, "e", null),
        TreeRow(2, "f.kt", 1),
        TreeRow(1, "g.kt", 2),
      ),
      compactTree(tree),
    )
  }

  @Test
  fun compactLowersDepthOfNestedChains() {
    val tree = buildFileTree(listOf(file("a/b/c/d/e/f.kt"), file("a/b/g.kt")))
    assertEquals(
      listOf(
        TreeRow(0, "a/b", null),
        TreeRow(1, "c/d/e", null),
        TreeRow(2, "f.kt", 0),
        TreeRow(1, "g.kt", 1),
      ),
      compactTree(tree),
    )
  }

  @Test
  fun compactLeavesFlatTreeUnchanged() {
    val tree = buildFileTree(listOf(file("a.kt"), file("src/b.kt"), file("src/c.kt")))
    assertEquals(tree, compactTree(tree))
  }

  @Test
  fun compactAppliesToFilteredRows() {
    assertEquals(
      listOf(TreeRow(0, "lib/x", null), TreeRow(1, "deep.kt", 4)),
      compactTree(filterTree(filterRows, filterFiles, "deep")),
    )
  }

  @Test
  fun compactedDirectoryCollapsesByItsFullPath() {
    val tree = compactTree(buildFileTree(listOf(file("a/b/c/d.kt"), file("a/b/c/e/f.kt"), file("g.kt"))))
    assertEquals(
      listOf(
        TreeEntry(TreeRow(0, "a/b/c", null), DirectoryPath(null, "a/b/c"), false),
        TreeEntry(TreeRow(1, "e", null), DirectoryPath(null, "a/b/c/e"), true),
        TreeEntry(TreeRow(1, "d.kt", 0), null, false),
        TreeEntry(TreeRow(0, "g.kt", 2), null, false),
      ),
      visibleTree(tree, setOf("a/b/c/e")),
    )
  }

  @Test
  fun directoryPathMatchesTheJoinedStringRegardlessOfSegmentation() {
    val chained = DirectoryPath(DirectoryPath(DirectoryPath(null, "a"), "b"), "c")
    val joined = DirectoryPath(null, "a/b/c")
    assertEquals(joined, chained)
    assertEquals("a/b/c".hashCode(), chained.hashCode())
    assertEquals("a/b/c", chained.toString())
    assertNotEquals(DirectoryPath(null, "a/b/d"), chained)
    assertNotEquals(DirectoryPath(null, "a/bc"), DirectoryPath(DirectoryPath(null, "a"), "b"))
  }

  @Test
  fun hashCollisionDoesNotCollapseAnotherDirectory() {
    assertEquals("Aa".hashCode(), "BB".hashCode())
    val tree = buildFileTree(listOf(file("Aa/x.kt"), file("BB/y.kt")))
    assertEquals(
      listOf(
        TreeEntry(TreeRow(0, "Aa", null), DirectoryPath(null, "Aa"), true),
        TreeEntry(TreeRow(0, "BB", null), DirectoryPath(null, "BB"), false),
        TreeEntry(TreeRow(1, "y.kt", 1), null, false),
      ),
      visibleTree(tree, setOf("Aa")),
    )
  }

  @Test
  fun segmentsBeyondMaxTreeDepthFoldIntoTheFileName() {
    val segments = List(10_000) { "d$it" }
    val tree = buildFileTree(listOf(file(segments.joinToString("/"))))
    assertEquals(MAX_TREE_DEPTH, tree.size)
    assertEquals(TreeRow(MAX_TREE_DEPTH - 1, segments.drop(MAX_TREE_DEPTH - 1).joinToString("/"), 0), tree.last())
    assertTrue(tree.all { it.depth < MAX_TREE_DEPTH })
  }

  @Test
  fun deepTreeWithFilesAtEveryLevelCollapsesByFullPath() {
    val files = (1 until MAX_TREE_DEPTH).map { depth -> file(List(depth) { "d" }.joinToString("/") + "/f.kt") }
    val tree = compactTree(buildFileTree(files))
    val collapsedPath = List(MAX_TREE_DEPTH / 2) { "d" }.joinToString("/")
    val entries = visibleTree(tree, setOf(collapsedPath))
    assertEquals(MAX_TREE_DEPTH - 1, entries.size)
    assertEquals(collapsedPath, entries.single { it.collapsed }.directoryPath.toString())
  }
}

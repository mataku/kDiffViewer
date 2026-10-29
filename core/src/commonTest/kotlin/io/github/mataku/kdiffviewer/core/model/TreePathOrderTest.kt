package io.github.mataku.kdiffviewer.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TreePathOrderTest {
  @Test
  fun directoriesComeBeforeFilesAtEveryLevel() {
    val paths = listOf("README.md", "m.txt", "app/x.kt", "app/src/Main.kt", "a-b.txt", "a/renamed.txt", "z/y.kt")
    assertEquals(
      listOf("a/renamed.txt", "app/src/Main.kt", "app/x.kt", "z/y.kt", "README.md", "a-b.txt", "m.txt"),
      paths.sortedWith(TreePathOrder),
    )
  }

  @Test
  fun namesCompareAsStringsWithinALevel() {
    assertEquals(listOf("B.kt", "a.kt", "b.kt"), listOf("b.kt", "a.kt", "B.kt").sortedWith(TreePathOrder))
  }

  @Test
  fun aDirectoryAndAFileWithTheSameNameKeepTheDirectoryFirst() {
    assertEquals(listOf("a/b.kt", "a"), listOf("a", "a/b.kt").sortedWith(TreePathOrder))
  }

  @Test
  fun equalPathsCompareEqual() {
    assertEquals(0, TreePathOrder.compare("a/b.kt", "a/b.kt"))
  }
}

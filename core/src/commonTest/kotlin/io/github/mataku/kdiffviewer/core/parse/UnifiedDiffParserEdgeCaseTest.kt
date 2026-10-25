package io.github.mataku.kdiffviewer.core.parse

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UnifiedDiffParserEdgeCaseTest {
  private suspend fun parse(text: String): List<FileDiff> = UnifiedDiffParser.parse(splitLines(text).asFlow()).toList()

  private fun hunks(file: FileDiff) = (file.content as DiffContent.Text).hunks

  @Test
  fun addedFile() = runTest {
    val file = parse(
      """
            diff --git a/new.txt b/new.txt
            new file mode 100644
            index 0000000..1111111
            --- /dev/null
            +++ b/new.txt
            @@ -0,0 +1,2 @@
            +one
            +two
      """.trimIndent() + "\n",
    ).single()
    assertEquals(null, file.oldPath)
    assertEquals("new.txt", file.newPath)
    assertEquals(ChangeType.Added, file.change)
    assertEquals(
      listOf(DiffLine(LineKind.Added, "one", null, 1), DiffLine(LineKind.Added, "two", null, 2)),
      hunks(file).single().lines,
    )
  }

  @Test
  fun deletedFile() = runTest {
    val file = parse(
      """
            diff --git a/old.txt b/old.txt
            deleted file mode 100644
            index 1111111..0000000
            --- a/old.txt
            +++ /dev/null
            @@ -1 +0,0 @@
            -gone
      """.trimIndent() + "\n",
    ).single()
    assertEquals("old.txt", file.oldPath)
    assertEquals(null, file.newPath)
    assertEquals(ChangeType.Deleted, file.change)
  }

  @Test
  fun pureRename() = runTest {
    val file = parse(
      """
            diff --git a/a.txt b/dir/b.txt
            similarity index 100%
            rename from a.txt
            rename to dir/b.txt
      """.trimIndent() + "\n",
    ).single()
    assertEquals("a.txt", file.oldPath)
    assertEquals("dir/b.txt", file.newPath)
    assertEquals(ChangeType.Renamed, file.change)
    assertEquals(DiffContent.Text(emptyList()), file.content)
  }

  @Test
  fun renameWithChanges() = runTest {
    val file = parse(
      """
            diff --git a/a.txt b/b.txt
            similarity index 80%
            rename from a.txt
            rename to b.txt
            index 1111111..2222222 100644
            --- a/a.txt
            +++ b/b.txt
            @@ -1 +1 @@
            -x
            +y
      """.trimIndent() + "\n",
    ).single()
    assertEquals(ChangeType.Renamed, file.change)
    assertEquals("a.txt", file.oldPath)
    assertEquals("b.txt", file.newPath)
    assertEquals(1, hunks(file).size)
  }

  @Test
  fun modeOnlyChange() = runTest {
    val file = parse(
      """
            diff --git a/run.sh b/run.sh
            old mode 100644
            new mode 100755
      """.trimIndent() + "\n",
    ).single()
    assertEquals(ChangeType.Modified, file.change)
    assertEquals("run.sh", file.oldPath)
    assertEquals("run.sh", file.newPath)
    assertEquals(DiffContent.Text(emptyList()), file.content)
  }

  @Test
  fun binaryFile() = runTest {
    val file = parse(
      """
            diff --git a/img.png b/img.png
            index 1111111..2222222 100644
            Binary files a/img.png and b/img.png differ
      """.trimIndent() + "\n",
    ).single()
    assertEquals(ChangeType.Modified, file.change)
    assertEquals(DiffContent.Binary, file.content)
  }

  @Test
  fun addedBinaryFile() = runTest {
    val file = parse(
      """
            diff --git a/img.png b/img.png
            new file mode 100644
            index 0000000..2222222
            Binary files /dev/null and b/img.png differ
      """.trimIndent() + "\n",
    ).single()
    assertEquals(ChangeType.Added, file.change)
    assertEquals(DiffContent.Binary, file.content)
    assertEquals(null, file.oldPath)
    assertEquals("img.png", file.newPath)
  }

  @Test
  fun noNewlineAtEof() = runTest {
    val file = parse(
      "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n-a\n\\ No newline at end of file\n+a\n\\ No newline at end of file\n",
    ).single()
    assertEquals(
      listOf(
        LineKind.Removed,
        LineKind.NoNewlineAtEof,
        LineKind.Added,
        LineKind.NoNewlineAtEof,
      ),
      hunks(file).single().lines.map { it.kind },
    )
    assertEquals(
      listOf(null, null),
      hunks(file).single().lines.filter { it.kind == LineKind.NoNewlineAtEof }.map { it.oldLineNo ?: it.newLineNo },
    )
  }

  @Test
  fun pathWithSpacesHasTrailingTabStripped() = runTest {
    val file = parse(
      "diff --git a/my file.txt b/my file.txt\n--- a/my file.txt\t\n+++ b/my file.txt\t\n@@ -1 +1 @@\n-a\n+b\n",
    ).single()
    assertEquals("my file.txt", file.oldPath)
    assertEquals("my file.txt", file.newPath)
  }

  @Test
  fun nonAsciiPath() = runTest {
    val file = parse(
      "diff --git a/日本語.txt b/日本語.txt\nold mode 100644\nnew mode 100755\n",
    ).single()
    assertEquals("日本語.txt", file.newPath)
    assertEquals("日本語.txt", file.oldPath)
  }

  @Test
  fun quotedPath() = runTest {
    val file = parse(
      "diff --git \"a/tab\\there\" \"b/tab\\there\"\n--- \"a/tab\\there\"\n+++ \"b/tab\\there\"\n@@ -1 +1 @@\n-a\n+b\n",
    ).single()
    assertEquals("tab\there", file.oldPath)
    assertEquals("tab\there", file.newPath)
  }

  @Test
  fun crInsideLineIsContent() = runTest {
    val file = parse(
      "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -0,0 +1 @@\n+a\rb\n",
    ).single()
    assertEquals("a\rb", hunks(file).single().lines.single().text)
  }

  @Test
  fun emptyLineInsideHunkIsEmptyContext() = runTest {
    val file = parse(
      "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1,3 +1,3 @@\n one\n\n-three\n+THREE\n",
    ).single()
    assertEquals(
      listOf(
        DiffLine(LineKind.Context, "one", 1, 1),
        DiffLine(LineKind.Context, "", 2, 2),
        DiffLine(LineKind.Removed, "three", 3, null),
        DiffLine(LineKind.Added, "THREE", null, 3),
      ),
      hunks(file).single().lines,
    )
  }

  @Test
  fun malformedFileBecomesUnparseableAndNextFileStillParses() = runTest {
    val files = parse(
      """
            diff --git a/bad.txt b/bad.txt
            --- a/bad.txt
            +++ b/bad.txt
            @@ -1,2 +1,2 @@
            garbage
            more garbage
            diff --git a/good.txt b/good.txt
            --- a/good.txt
            +++ b/good.txt
            @@ -1 +1 @@
            -a
            +b
      """.trimIndent() + "\n",
    )
    assertEquals(2, files.size)
    assertEquals(DiffContent.Unparseable, files[0].content)
    assertEquals("bad.txt", files[0].newPath)
    assertFalse(files[1].content is DiffContent.Unparseable)
    assertEquals("good.txt", files[1].newPath)
  }

  @Test
  fun truncatedHunkIsUnparseable() = runTest {
    val file = parse(
      "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1,3 +1,3 @@\n one\n",
    ).single()
    assertEquals(DiffContent.Unparseable, file.content)
  }
}

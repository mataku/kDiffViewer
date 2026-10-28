package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.DiffStats
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals

class UntrackedFileTest {
  private fun regular(text: String) = UntrackedEntry.Regular(text.encodeToByteArray())

  private fun texts(diff: FileDiff): List<String> = (diff.content as DiffContent.Text).hunks.single().lines.map { it.text }

  @Test
  fun textFileBecomesAllAddedLines() {
    val diff = synthesizeUntracked("n.txt", regular("a\nb\n"))
    assertEquals(
      FileDiff(
        null,
        "n.txt",
        ChangeType.Added,
        isUntracked = true,
        DiffContent.Text(
          listOf(
            Hunk(
              0,
              0,
              1,
              2,
              "",
              listOf(
                DiffLine(LineKind.Added, "a", null, 1),
                DiffLine(LineKind.Added, "b", null, 2),
              ),
            ),
          ),
        ),
      ),
      diff,
    )
  }

  @Test
  fun missingTrailingNewlineAddsMarker() {
    val diff = synthesizeUntracked("n.txt", regular("a"))
    assertEquals(
      DiffContent.Text(
        listOf(
          Hunk(
            0,
            0,
            1,
            1,
            "",
            listOf(
              DiffLine(LineKind.Added, "a", null, 1),
              DiffLine(LineKind.NoNewlineAtEof, "", null, null),
            ),
          ),
        ),
      ),
      diff.content,
    )
  }

  @Test
  fun crlfIsStripped() {
    assertEquals(listOf("a", "b"), texts(synthesizeUntracked("n.txt", regular("a\r\nb\r\n"))))
  }

  @Test
  fun emptyFileHasNoHunks() {
    val diff = synthesizeUntracked("e", UntrackedEntry.Regular(ByteArray(0)))
    assertEquals(DiffContent.Text(emptyList()), diff.content)
  }

  @Test
  fun nulInFirst8000BytesIsBinary() {
    val diff = synthesizeUntracked("b", UntrackedEntry.Regular(ByteArray(10) { 0 }))
    assertEquals(DiffContent.Binary, diff.content)
    assertEquals(ChangeType.Added, diff.change)
    assertEquals(true, diff.isUntracked)
  }

  @Test
  fun nulAfter8000BytesIsText() {
    val bytes = ByteArray(8001) { if (it == 8000) 0 else 'a'.code.toByte() }
    val diff = synthesizeUntracked("t", UntrackedEntry.Regular(bytes))
    assertEquals(true, diff.content is DiffContent.Text)
  }

  @Test
  fun invalidUtf8IsReplaced() {
    val bytes = byteArrayOf(0xC3.toByte(), '\n'.code.toByte())
    assertEquals(listOf("�"), texts(synthesizeUntracked("u", UntrackedEntry.Regular(bytes))))
  }

  @Test
  fun tooLarge() {
    val diff = synthesizeUntracked("big", UntrackedEntry.TooLarge)
    assertEquals(DiffContent.TooLarge, diff.content)
    assertEquals(ChangeType.Added, diff.change)
    assertEquals(true, diff.isUntracked)
  }

  @Test
  fun symlinkShowsTargetWithoutNewline() {
    val diff = synthesizeUntracked("l", UntrackedEntry.Symlink("../x"))
    assertEquals(
      DiffContent.Text(
        listOf(
          Hunk(
            0,
            0,
            1,
            1,
            "",
            listOf(
              DiffLine(LineKind.Added, "../x", null, 1),
              DiffLine(LineKind.NoNewlineAtEof, "", null, null),
            ),
          ),
        ),
      ),
      diff.content,
    )
  }

  @Test
  fun unreadable() {
    val diff = synthesizeUntracked("x", UntrackedEntry.Unreadable)
    assertEquals(DiffContent.Unparseable, diff.content)
    assertEquals(ChangeType.Added, diff.change)
    assertEquals(true, diff.isUntracked)
  }

  @Test
  fun untrackedOverLineCapBecomesTooManyLines() {
    assertEquals(DiffContent.TooManyLines(DiffStats(3, 0)), synthesizeUntracked("n.txt", regular("a\nb\nc\n"), maxLines = 2).content)
    assertEquals(3, texts(synthesizeUntracked("n.txt", regular("a\nb\nc\n"), maxLines = 3)).size)
  }
}

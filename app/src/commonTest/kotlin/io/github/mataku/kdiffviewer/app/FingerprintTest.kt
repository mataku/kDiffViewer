package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FingerprintTest {
  private fun lines(vararg texts: String) = texts.mapIndexed { index, text -> DiffLine(LineKind.Context, text, index + 1, index + 1) }

  private fun diff(lines: List<DiffLine> = lines("a", "b"), header: String = " fun main()") = FileDiff(
    "a.kt",
    "a.kt",
    ChangeType.Modified,
    false,
    DiffContent.Text(listOf(Hunk(1, lines.size, 1, lines.size, header, lines))),
  )

  @Test
  fun equalDiffsHaveEqualFingerprints() {
    assertEquals(diff().fingerprint(), diff().fingerprint())
  }

  @Test
  fun anyChangeChangesTheFingerprint() {
    val base = diff()
    val variants = listOf(
      diff(lines("a", "c")),
      diff(lines("a", "b").mapIndexed { i, line -> if (i == 1) line.copy(kind = LineKind.Added) else line }),
      diff(lines("a", "b").mapIndexed { i, line -> if (i == 1) line.copy(newLineNo = 9) else line }),
      base.copy(newPath = "b.kt"),
      base.copy(change = ChangeType.Renamed),
      base.copy(isUntracked = true),
      base.copy(content = DiffContent.Binary),
      base.copy(content = DiffContent.TooLarge),
      diff(header = " fun other()"),
    )
    for (variant in variants) assertNotEquals(base.fingerprint(), variant.fingerprint(), "$variant")
  }

  @Test
  fun stringHashCollisionsDoNotCollide() {
    assertNotEquals(diff(lines("Aa")).fingerprint(), diff(lines("BB")).fingerprint())
  }

  @Test
  fun fieldBoundariesMatter() {
    assertNotEquals(diff(lines("ab", "c")).fingerprint(), diff(lines("a", "bc")).fingerprint())
  }
}

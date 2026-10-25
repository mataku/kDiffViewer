package io.github.mataku.kdiffviewer.app.highlight

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.Side
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighlightPipelineJvmTest {
  @Test
  fun nativeLoadFailureStopsRunSilently() = runTest {
    val paths = (1..3).map { "$it.kt" }
    val source = FakeContentSource(paths.associate { (it to Side.New) to "x" })
    val highlighter = FakeHighlighter().apply { onCall = { throw UnsatisfiedLinkError("no lib") } }
    val files = paths.map { textFile(null, it, listOf(line(LineKind.Added, "x", null, 1)), change = ChangeType.Added) }
    val failures = mutableListOf<Throwable>()
    val (_, slots) = runEntries(pipeline(source, highlighter) { failures += it }, *files.toTypedArray())

    assertEquals(emptyList(), failures)
    assertEquals(1, highlighter.calls.size)
    assertTrue(slots.all { it.value == null })
    assertTrue(source.closed)
    assertTrue(backgroundScope.isActive)
  }
}

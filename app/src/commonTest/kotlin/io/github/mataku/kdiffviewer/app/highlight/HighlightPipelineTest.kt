@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.mataku.kdiffviewer.app.highlight

import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.compose.highlight.core.HighlightedLine
import io.github.mataku.kdiffviewer.core.git.ContentSession
import io.github.mataku.kdiffviewer.core.git.ContentSource
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.Side
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeContentSource(private val texts: Map<Pair<String, Side>, String>) : ContentSource {
  var opens = 0
  var closed = false
  val reads = mutableListOf<Pair<String, Side>>()

  override suspend fun open(repo: RepoPath): ContentSession {
    opens++
    return object : ContentSession {
      override suspend fun read(file: FileDiff, side: Side, maxBytes: Long): String? {
        val path = (if (side == Side.Old) file.oldPath else file.newPath)!!
        reads += path to side
        return texts[path to side]
      }

      override fun close() {
        closed = true
      }
    }
  }
}

class FakeHighlighter(
  var spansFor: (language: String, text: String) -> List<HighlightSpan> = { language, text ->
    listOf(HighlightSpan(0, text.length, "$language:$text"))
  },
) : LineHighlighter<String> {
  val calls = mutableListOf<String>()
  var onCall: (Int) -> Unit = {}

  override fun highlight(code: String, language: String, ranges: List<IntRange>): List<HighlightedLine> {
    calls += code
    onCall(calls.size)
    val lines = code.split('\n')
    return ranges.flatMap { range -> range.map { HighlightedLine(it, lines[it - 1], spansFor(language, lines[it - 1])) } }
  }
}

fun line(kind: LineKind, text: String, old: Int?, new: Int?) = DiffLine(kind, text, old, new)

fun textFile(oldPath: String?, newPath: String?, vararg hunks: List<DiffLine>, change: ChangeType = ChangeType.Modified) = FileDiff(oldPath, newPath, change, false, DiffContent.Text(hunks.map { Hunk(1, 0, 1, 0, "@@", it) }))

fun span(text: String) = listOf(HighlightSpan(0, text.length, "kotlin:$text"))

fun TestScope.pipeline(
  source: ContentSource,
  highlighter: LineHighlighter<String>,
  onFailure: (Throwable) -> Unit = { throw AssertionError("unexpected failure", it) },
) = HighlightPipeline(
  source,
  LanguageResolver(mapOf(LanguageId.Kotlin to { "kotlin" })),
  highlighter,
  StandardTestDispatcher(testScheduler),
  workers = 1,
  onFailure = onFailure,
)

fun TestScope.runEntries(pipeline: HighlightPipeline<String>, vararg files: FileDiff): Pair<HighlightRun, List<HighlightSlot>> {
  val run = pipeline.start(backgroundScope, RepoPath("/repo"))
  val slots = files.map { HighlightSlot() }
  run.submit(files.mapIndexed { index, file -> HighlightEntry(index, file, slots[index]) })
  runCurrent()
  return run to slots
}

class HighlightPipelineTest {
  private val modified = textFile(
    "A.kt",
    "A.kt",
    listOf(
      line(LineKind.Context, "a", 1, 1),
      line(LineKind.Removed, "b", 2, null),
      line(LineKind.Added, "B", null, 2),
      line(LineKind.Context, "c", 3, 3),
    ),
    listOf(
      line(LineKind.Context, "e", 5, 5),
      line(LineKind.Removed, "f", 6, null),
      line(LineKind.Added, "F", null, 6),
      line(LineKind.NoNewlineAtEof, "\\ No newline at end of file", null, null),
    ),
  )
  private val modifiedTexts = mapOf(
    ("A.kt" to Side.Old) to "a\nb\nc\nd\ne\nf",
    ("A.kt" to Side.New) to "a\nB\nc\nd\ne\nF",
  )

  private fun addition(path: String) = textFile(null, path, listOf(line(LineKind.Added, "x", null, 1)), change = ChangeType.Added)

  @Test
  fun writesSpansForShownLines() = runTest {
    val source = FakeContentSource(modifiedTexts)
    val (_, slots) = runEntries(pipeline(source, FakeHighlighter()), modified)

    val spans = assertNotNull(slots[0].value).spans
    assertEquals(2, spans.size)
    assertContentEquals(arrayOf(span("a"), span("b"), span("B"), span("c")), spans[0])
    assertContentEquals(arrayOf(span("e"), span("f"), span("F"), null), spans[1])
  }

  @Test
  fun textMismatchLeavesLinePlain() = runTest {
    val source = FakeContentSource(
      mapOf(("A.kt" to Side.New) to "a\nx"),
    )
    val file = textFile("A.kt", "A.kt", listOf(line(LineKind.Context, "a", 1, 1), line(LineKind.Added, "y", null, 2)))
    val (_, slots) = runEntries(pipeline(source, FakeHighlighter()), file)

    val spans = assertNotNull(slots[0].value).spans
    assertContentEquals(arrayOf(span("a"), null), spans[0])
  }

  @Test
  fun fileWithoutSpansKeepsSlotEmpty() = runTest {
    val source = FakeContentSource(modifiedTexts)
    val (_, slots) = runEntries(pipeline(source, FakeHighlighter { _, _ -> emptyList() }), modified)

    assertNull(slots[0].value)
  }

  @Test
  fun skipsOldSideWithoutRemovedLines() = runTest {
    val source = FakeContentSource(mapOf(("N.kt" to Side.New) to "x"))
    val (_, slots) = runEntries(pipeline(source, FakeHighlighter()), addition("N.kt"))

    assertEquals(listOf("N.kt" to Side.New), source.reads)
    assertContentEquals(arrayOf(span("x")), assertNotNull(slots[0].value).spans[0])
  }

  @Test
  fun skipsSideWithoutLanguage() = runTest {
    val source = FakeContentSource(mapOf(("notes.txt" to Side.New) to "x"))
    val (_, slots) = runEntries(pipeline(source, FakeHighlighter()), addition("notes.txt"))

    assertEquals(emptyList(), source.reads)
    assertEquals(0, source.opens)
    assertNull(slots[0].value)
  }

  @Test
  fun languageIsResolvedPerSidePath() = runTest {
    val source = FakeContentSource(
      mapOf(("notes.txt" to Side.Old) to "a\nb", ("Notes.kt" to Side.New) to "a\nB"),
    )
    val file = textFile(
      "notes.txt",
      "Notes.kt",
      listOf(line(LineKind.Removed, "b", 2, null), line(LineKind.Added, "B", null, 2)),
      change = ChangeType.Renamed,
    )
    val (_, slots) = runEntries(pipeline(source, FakeHighlighter()), file)

    assertEquals(listOf("Notes.kt" to Side.New), source.reads)
    assertContentEquals(arrayOf(null, span("B")), assertNotNull(slots[0].value).spans[0])
  }

  @Test
  fun unreadableSideLeavesItsLinesPlain() = runTest {
    val source = FakeContentSource(mapOf(("A.kt" to Side.New) to "a\nB\nc\nd\ne\nF"))
    val (_, slots) = runEntries(pipeline(source, FakeHighlighter()), modified)

    val spans = assertNotNull(slots[0].value).spans
    assertContentEquals(arrayOf(span("a"), null, span("B"), span("c")), spans[0])
    assertContentEquals(arrayOf(span("e"), null, span("F"), null), spans[1])
  }

  @Test
  fun processesEntriesInSubmitOrder() = runTest {
    val source = FakeContentSource(
      mapOf(("1.kt" to Side.New) to "one", ("2.kt" to Side.New) to "two", ("3.kt" to Side.New) to "three"),
    )
    val highlighter = FakeHighlighter()
    runEntries(pipeline(source, highlighter), addition("1.kt"), addition("2.kt"), addition("3.kt"))

    assertEquals(listOf("one", "two", "three"), highlighter.calls)
  }

  @Test
  fun cancelStopsRunAndClosesSession() = runTest {
    val source = FakeContentSource(mapOf(("1.kt" to Side.New) to "x", ("2.kt" to Side.New) to "x"))
    val (run, slots) = runEntries(pipeline(source, FakeHighlighter()), addition("1.kt"))
    assertNotNull(slots[0].value)

    run.cancel()
    val second = HighlightSlot()
    run.submit(listOf(HighlightEntry(1, addition("2.kt"), second)))
    runCurrent()

    assertNull(second.value)
    assertTrue(source.closed)
    assertEquals(listOf("1.kt" to Side.New), source.reads)
  }

  @Test
  fun sessionOpenedOncePerRun() = runTest {
    val paths = (1..5).map { "$it.kt" }
    val source = FakeContentSource(paths.associate { (it to Side.New) to "x" })
    val (run, slots) = runEntries(pipeline(source, FakeHighlighter()), *paths.map { addition(it) }.toTypedArray())

    assertEquals(1, source.opens)
    assertTrue(slots.all { it.value != null })
    run.cancel()
  }

  @Test
  fun failureStopsRunAndReportsIt() = runTest {
    val paths = (1..3).map { "$it.kt" }
    val source = FakeContentSource(paths.associate { (it to Side.New) to "x" })
    val highlighter = FakeHighlighter().apply { onCall = { throw IllegalStateException("boom") } }
    val failures = mutableListOf<Throwable>()
    val (_, slots) = runEntries(pipeline(source, highlighter) { failures += it }, *paths.map { addition(it) }.toTypedArray())

    assertEquals(1, failures.size)
    assertTrue(failures[0] is IllegalStateException)
    assertEquals(1, highlighter.calls.size)
    assertTrue(slots.all { it.value == null })
    assertTrue(source.closed)
    assertTrue(backgroundScope.isActive)
  }
}

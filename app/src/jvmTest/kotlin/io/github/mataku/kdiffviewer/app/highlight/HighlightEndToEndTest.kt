package io.github.mataku.kdiffviewer.app.highlight

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import io.github.mataku.compose.highlight.core.highlightLines
import io.github.mataku.kdiffviewer.app.DiffViewerState
import io.github.mataku.kdiffviewer.app.ui.KdvTheme
import io.github.mataku.kdiffviewer.app.ui.LightSyntaxTheme
import io.github.mataku.kdiffviewer.app.ui.ViewerScreen
import io.github.mataku.kdiffviewer.app.ui.lineText
import io.github.mataku.kdiffviewer.core.git.ContentSession
import io.github.mataku.kdiffviewer.core.git.ContentSource
import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.Side
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HighlightEndToEndTest {
  private class InMemoryDiffSource(var files: List<FileDiff>) : DiffSource {
    override fun load(repo: RepoPath): Flow<FileDiff> = files.asFlow()
  }

  private class InMemoryContentSource(
    private val texts: (session: Int, path: String, side: Side) -> String?,
    private val beforeRead: suspend (session: Int) -> Unit = {},
  ) : ContentSource {
    private val opens = AtomicInteger()
    val reads = ConcurrentLinkedQueue<String>()

    override suspend fun open(repo: RepoPath): ContentSession {
      val session = opens.incrementAndGet()
      return object : ContentSession {
        override suspend fun read(file: FileDiff, side: Side, maxBytes: Long): String? {
          val path = (if (side == Side.Old) file.oldPath else file.newPath)!!
          reads += path
          beforeRead(session)
          return texts(session, path, side)
        }

        override fun close() {}
      }
    }
  }

  private fun modified(path: String, vararg lines: DiffLine) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(Hunk(1, 1, 1, 1, "", lines.toList()))))

  private fun added(path: String, text: String) = FileDiff(null, path, ChangeType.Added, false, DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", listOf(DiffLine(LineKind.Added, text, null, 1))))))

  private val commentLines = listOf("package a", "", "/*", " * one", " * two", " * three", " */", "fun x() = 1")

  private val commentFile = modified(
    "A.kt",
    DiffLine(LineKind.Context, " * two", 5, 5),
    DiffLine(LineKind.Added, " * three", null, 6),
    DiffLine(LineKind.Context, " */", 6, 7),
  )

  private fun AnnotatedString.hasSpan(start: Int, end: Int, captureName: String): Boolean {
    val color = LightSyntaxTheme.resolve(captureName)!!.color
    return spanStyles.any { it.start == start && it.end == end && it.item.color == color }
  }

  private fun viewer(source: DiffSource, content: ContentSource, scope: CoroutineScope) = DiffViewerState(
    source = source,
    scope = scope,
    batchWindowMillis = 0,
    highlightPipeline = HighlightPipeline(
      content,
      LanguageResolver(defaultLanguageAccessors),
      LineHighlighter(::highlightLines),
      Dispatchers.Default,
    ),
  )

  private fun DiffViewerState.slotWritten(fileIndex: Int, loadId: Long = 1): Boolean {
    Snapshot.sendApplyNotifications()
    val ui = uiState.value
    return ui.loadId == loadId && ui.highlights.getOrNull(fileIndex)?.value != null
  }

  private fun runViewer(source: DiffSource, content: ContentSource, test: ComposeUiTest.(DiffViewerState) -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
      runComposeUiTest {
        val state = viewer(source, content, scope)
        setContent {
          val ui by state.uiState.collectAsState()
          KdvTheme(darkTheme = false) { ViewerScreen(ui, state::reload) }
        }
        state.open(RepoPath("/repo"))
        test(state)
      }
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun multiLineCommentIsColoredInsideHunk() {
    val content = InMemoryContentSource({ _, _, side -> if (side == Side.New) commentLines.joinToString("\n", postfix = "\n") else null })
    runViewer(InMemoryDiffSource(listOf(commentFile)), content) { state ->
      waitUntil(timeoutMillis = 10_000) { state.slotWritten(0) }
      waitForIdle()
      assertTrue(lineText(" * three").hasSpan(0, " * three".length, "comment"))
    }
  }

  @Test
  fun crlfFileIsColored() {
    val content = InMemoryContentSource({ _, _, side -> if (side == Side.New) commentLines.joinToString("\r\n", postfix = "\r\n") else null })
    runViewer(InMemoryDiffSource(listOf(commentFile)), content) { state ->
      waitUntil(timeoutMillis = 10_000) { state.slotWritten(0) }
      waitForIdle()
      assertTrue(lineText(" * three").hasSpan(0, " * three".length, "comment"))
    }
  }

  @Test
  fun unsupportedFileStaysPlain() {
    val notes = added("notes.txt", "val notes")
    val content = InMemoryContentSource({ _, path, side -> if (path == "A.kt" && side == Side.New) commentLines.joinToString("\n", postfix = "\n") else "val notes\n" })
    runViewer(InMemoryDiffSource(listOf(commentFile, notes)), content) { state ->
      waitUntil(timeoutMillis = 10_000) { state.slotWritten(0) }
      waitForIdle()
      assertEquals(emptyList(), lineText("val notes").spanStyles)
      assertNull(state.uiState.value.highlights[1].value)
      assertFalse("notes.txt" in content.reads)
    }
  }

  @Test
  fun reloadShowsColorsForNewLoad() {
    val firstReadStarted = CompletableDeferred<Unit>()
    val releaseFirstRead = CompletableDeferred<Unit>()
    val firstReadReturned = CompletableDeferred<Unit>()
    val content = InMemoryContentSource(
      texts = { session, _, _ -> if (session == 1) "val a = 1\n" else "val b = 2\n" },
      beforeRead = { session ->
        if (session == 1) {
          firstReadStarted.complete(Unit)
          withContext(NonCancellable) { releaseFirstRead.await() }
          firstReadReturned.complete(Unit)
        }
      },
    )
    val source = InMemoryDiffSource(listOf(added("A.kt", "val a = 1")))
    runViewer(source, content) { state ->
      waitUntil(timeoutMillis = 10_000) { firstReadStarted.isCompleted && state.uiState.value.highlights.isNotEmpty() }
      val firstSlots = state.uiState.value.highlights
      val firstValues = firstSlots.map { it.value }
      source.files = listOf(added("A.kt", "val b = 2"))
      state.reload()
      waitUntil(timeoutMillis = 10_000) { state.slotWritten(0, loadId = 2) }
      waitForIdle()
      assertTrue(lineText("val b = 2").hasSpan(0, 3, "keyword"))

      releaseFirstRead.complete(Unit)
      waitUntil(timeoutMillis = 10_000) { firstReadReturned.isCompleted }
      Thread.sleep(300)
      Snapshot.sendApplyNotifications()
      assertEquals(1, firstSlots.size)
      firstSlots.zip(firstValues).forEach { (slot, value) -> assertTrue(slot.value === value) }
    }
  }
}

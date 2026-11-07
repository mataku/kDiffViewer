package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.git.UntrackedEntry
import io.github.mataku.kdiffviewer.core.git.WorkingTreeReader
import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.ExpandDirection
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DiffViewerRenderedTest {
  private val repo = RepoPath("/repo")

  private val lines = List(100) { "L${it + 1}" }

  private fun context(line: Int) = DiffLine(LineKind.Context, "L$line", line, line)

  private fun replaceHunk(line: Int, text: String = "L$line") = Hunk(
    line - 3,
    7,
    line - 3,
    7,
    "",
    listOf(
      context(line - 3),
      context(line - 2),
      context(line - 1),
      DiffLine(LineKind.Removed, "old", line, null),
      DiffLine(LineKind.Added, text, null, line),
      context(line + 1),
      context(line + 2),
      context(line + 3),
    ),
  )

  private fun file(path: String, firstText: String = "L11") = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(replaceHunk(11, firstText), replaceHunk(80))))

  private class FakeSource(var files: List<FileDiff>) : DiffSource {
    override suspend fun resolveBase(repo: RepoPath, spec: BaseSpec): ResolvedBase = ResolvedBase.Commit("HEAD", "h", "h")
    override fun load(repo: RepoPath, base: ResolvedBase): Flow<FileDiff> = flow { files.forEach { emit(it) } }
    override suspend fun head(repo: RepoPath): HeadRef? = null
    override suspend fun branches(repo: RepoPath): List<BranchRef> = emptyList()
    override suspend fun root(repo: RepoPath): RepoPath = repo
  }

  private class FakeWorkingTree(val entries: MutableMap<String, UntrackedEntry>) : WorkingTreeReader {
    var reads = 0

    override suspend fun read(root: String, relativePath: String, maxBytes: Long): UntrackedEntry {
      reads++
      return entries[relativePath] ?: UntrackedEntry.Unreadable
    }
  }

  private fun regular(text: List<String>) = UntrackedEntry.Regular(text.joinToString("\n", postfix = "\n").encodeToByteArray())

  private fun TestScope.stateWith(tree: FakeWorkingTree, source: FakeSource) = DiffViewerState(source, this, 16, workingTree = tree)

  private fun DiffViewerState.toggleRendered(fileIndex: Int) = setRendered(fileIndex, fileIndex !in uiState.value.rawBodies)

  private fun TestScope.opened(tree: FakeWorkingTree, vararg files: FileDiff): DiffViewerState {
    val state = stateWith(tree, FakeSource(files.toList()))
    state.open(repo)
    advanceUntilIdle()
    return state
  }

  private fun ViewerUiState.fileRows(fileIndex: Int): List<DiffRow> {
    val start = fileHeaderRowIndex[fileIndex]
    val end = fileHeaderRowIndex.getOrNull(fileIndex + 1) ?: rows.size
    return rows.subList(start, end)
  }

  private fun ViewerUiState.isRenderedBody(fileIndex: Int) = fileRows(fileIndex).drop(1).all { it is DiffRow.RenderedBlock || it is DiffRow.RemovedMarker || it is DiffRow.UnchangedFold }

  private fun ViewerUiState.lineTexts(fileIndex: Int) = fileRows(fileIndex).filterIsInstance<DiffRow.Line>().map { it.line.text }

  private fun ViewerUiState.errorOf(fileIndex: Int) = expansionErrors[CollapsedFile(files[fileIndex].newPath!!, fingerprints[fileIndex])]

  @Test
  fun pressingRenderedTwiceStaysRendered() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    state.setRendered(0, true)
    state.setRendered(0, true)
    advanceUntilIdle()
    assertEquals(setOf("a.md"), state.uiState.value.rendered)
    assertTrue(state.uiState.value.isRenderedBody(0))
    assertEquals(1, tree.reads)
  }

  @Test
  fun pressingRawWhileRenderingEndsInRaw() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    val raw = state.uiState.value.rows
    state.setRendered(0, true)
    state.setRendered(0, false)
    advanceUntilIdle()
    assertEquals(emptySet(), state.uiState.value.rendered)
    assertEquals(raw, state.uiState.value.rows)
  }

  @Test
  fun settingTheCurrentViewIsNoOp() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    val before = state.uiState.value
    state.setRendered(0, false)
    advanceUntilIdle()
    assertEquals(0, tree.reads)
    assertSame(before, state.uiState.value)
  }

  @Test
  fun toggleSwapsOnlyThatFile() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"), file("b.kt"))
    val before = state.uiState.value
    state.toggleRendered(0)
    advanceUntilIdle()
    val after = state.uiState.value
    assertTrue(after.isRenderedBody(0))
    assertEquals(setOf("a.md"), after.rendered)
    assertEquals(before.fileRows(1), after.fileRows(1))
    assertSame(before.files, after.files)
    assertTrue(after.contentId > before.contentId)
    assertTrue(after.rowsGeneration > before.rowsGeneration)
    assertEquals(splitRowsOf(after.rows), after.split)
  }

  @Test
  fun toggleBackRestoresRawWithoutReading() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    val before = state.uiState.value
    state.toggleRendered(0)
    advanceUntilIdle()
    val reads = tree.reads
    state.toggleRendered(0)
    advanceUntilIdle()
    val after = state.uiState.value
    assertEquals(reads, tree.reads)
    assertEquals(before.rows, after.rows)
    assertEquals(before.split, after.split)
    assertEquals(emptySet(), after.rendered)
  }

  @Test
  fun toggleBackKeepsExpandedContext() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    assertTrue("L34" in state.uiState.value.lineTexts(0))
    state.toggleRendered(0)
    advanceUntilIdle()
    state.toggleRendered(0)
    advanceUntilIdle()
    assertTrue("L34" in state.uiState.value.lineTexts(0))
  }

  @Test
  fun expandQueuedBehindAToggleIsIgnored() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    val raw = state.uiState.value
    state.toggleRendered(0)
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    val rendered = state.uiState.value
    assertTrue(rendered.isRenderedBody(0))
    assertSame(raw.files, rendered.files)
    state.toggleRendered(0)
    advanceUntilIdle()
    assertEquals(raw.rows, state.uiState.value.rows)
  }

  @Test
  fun expandingOtherFileKeepsRendered() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines), "b.kt" to regular(lines)))
    val state = opened(tree, file("a.md"), file("b.kt"))
    state.toggleRendered(0)
    advanceUntilIdle()
    state.expandContext(1, 1, ExpandDirection.Down)
    advanceUntilIdle()
    val after = state.uiState.value
    assertTrue(after.isRenderedBody(0))
    assertTrue("L34" in after.lineTexts(1))
  }

  @Test
  fun reloadKeepsRendered() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val source = FakeSource(listOf(file("a.md")))
    val state = stateWith(tree, source)
    state.open(repo)
    advanceUntilIdle()
    state.toggleRendered(0)
    advanceUntilIdle()
    val fingerprint = state.uiState.value.fingerprints[0]
    source.files = listOf(file("a.md", firstText = "edited"))
    tree.entries["a.md"] = regular(lines.map { if (it == "L11") "edited" else it })
    state.reload()
    advanceUntilIdle()
    val after = state.uiState.value
    assertTrue(after.fingerprints[0] != fingerprint)
    assertEquals(setOf("a.md"), after.rendered)
    assertTrue(after.isRenderedBody(0))
    assertTrue("edited" in (after.fileRows(0)[1] as DiffRow.RenderedBlock).source.block.toString())
  }

  @Test
  fun reloadReappliesExpansionAndRender() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    state.toggleRendered(0)
    advanceUntilIdle()
    state.reload()
    advanceUntilIdle()
    assertTrue(state.uiState.value.isRenderedBody(0))
    state.toggleRendered(0)
    advanceUntilIdle()
    assertTrue("L34" in state.uiState.value.lineTexts(0))
  }

  @Test
  fun reloadFailureLeavesRawWithError() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    state.toggleRendered(0)
    advanceUntilIdle()
    tree.entries.remove("a.md")
    state.reload()
    advanceUntilIdle()
    val after = state.uiState.value
    assertFalse(after.isRenderedBody(0))
    assertEquals(emptySet(), after.rendered)
    assertEquals(ExpansionError.Unreadable, after.errorOf(0))
  }

  @Test
  fun staleLeavesRaw() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines.map { if (it == "L10") "edited" else it })))
    val state = opened(tree, file("a.md"))
    val before = state.uiState.value.rows
    state.toggleRendered(0)
    advanceUntilIdle()
    val after = state.uiState.value
    assertEquals(before, after.rows)
    assertEquals(emptySet(), after.rendered)
    assertEquals(ExpansionError.RenderStale, after.errorOf(0))
  }

  @Test
  fun unreadableLeavesRaw() = runTest(StandardTestDispatcher()) {
    val state = opened(FakeWorkingTree(mutableMapOf()), file("a.md"))
    state.toggleRendered(0)
    advanceUntilIdle()
    val after = state.uiState.value
    assertEquals(emptySet(), after.rendered)
    assertEquals(ExpansionError.Unreadable, after.errorOf(0))
  }

  @Test
  fun readerTooLargeLeavesRaw() = runTest(StandardTestDispatcher()) {
    val state = opened(FakeWorkingTree(mutableMapOf("a.md" to UntrackedEntry.TooLarge)), file("a.md"))
    state.toggleRendered(0)
    advanceUntilIdle()
    assertEquals(ExpansionError.RenderTooLarge, state.uiState.value.errorOf(0))
  }

  @Test
  fun overRenderCapLeavesRaw() = runTest(StandardTestDispatcher()) {
    val padded = lines + List(40) { "x".repeat(30_000) }
    val state = opened(FakeWorkingTree(mutableMapOf("a.md" to regular(padded))), file("a.md"))
    state.toggleRendered(0)
    advanceUntilIdle()
    val after = state.uiState.value
    assertEquals(emptySet(), after.rendered)
    assertEquals(ExpansionError.RenderTooLarge, after.errorOf(0))
  }

  @Test
  fun parseFailureLeavesRaw() = runTest(StandardTestDispatcher()) {
    val deep = lines.toMutableList()
    deep[40] = "> ".repeat(100) + "L41"
    val state = opened(FakeWorkingTree(mutableMapOf("a.md" to regular(deep))), file("a.md"))
    state.toggleRendered(0)
    advanceUntilIdle()
    assertEquals(ExpansionError.RenderFailed, state.uiState.value.errorOf(0))
  }

  @Test
  fun successClearsError() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf())
    val state = opened(tree, file("a.md"))
    state.toggleRendered(0)
    advanceUntilIdle()
    tree.entries["a.md"] = regular(lines)
    state.toggleRendered(0)
    advanceUntilIdle()
    val after = state.uiState.value
    assertEquals(setOf("a.md"), after.rendered)
    assertEquals(null, after.errorOf(0))
  }

  @Test
  fun ineligibleIsNoOp() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.kt" to regular(lines)))
    val state = opened(tree, file("a.kt"))
    val before = state.uiState.value
    state.toggleRendered(0)
    advanceUntilIdle()
    assertEquals(0, tree.reads)
    assertEquals(before, state.uiState.value)
  }

  @Test
  fun ignoredWhileLoading() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    state.reload()
    state.toggleRendered(0)
    advanceUntilIdle()
    assertEquals(emptySet(), state.uiState.value.rendered)
    assertEquals(0, tree.reads)
  }

  @Test
  fun staleLoadTokenDropsResult() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("a.md" to regular(lines)))
    val state = opened(tree, file("a.md"))
    state.toggleRendered(0)
    state.reload()
    advanceUntilIdle()
    assertEquals(emptySet(), state.uiState.value.rendered)
    assertFalse(state.uiState.value.isRenderedBody(0))
  }
}

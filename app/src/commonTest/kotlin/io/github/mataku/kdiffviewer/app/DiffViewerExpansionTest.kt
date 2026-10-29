package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.app.highlight.FakeContentSource
import io.github.mataku.kdiffviewer.app.highlight.FakeHighlighter
import io.github.mataku.kdiffviewer.app.highlight.pipeline
import io.github.mataku.kdiffviewer.app.review.DraftMode
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.review.ReviewState
import io.github.mataku.kdiffviewer.app.review.sidePosition
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
import io.github.mataku.kdiffviewer.core.model.GapView
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.LineLocation
import io.github.mataku.kdiffviewer.core.review.Placement
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import io.github.mataku.kdiffviewer.core.review.Side as ReviewSide

class DiffViewerExpansionTest {
  private val repo = RepoPath("/repo")

  private val lines = List(100) { "L${it + 1}" }

  private fun context(line: Int) = DiffLine(LineKind.Context, "L$line", line, line)

  private fun replaceHunk(line: Int) = Hunk(
    line - 3,
    7,
    line - 3,
    7,
    "",
    listOf(context(line - 3), context(line - 2), context(line - 1), DiffLine(LineKind.Removed, "old", line, null), DiffLine(LineKind.Added, "L$line", null, line), context(line + 1), context(line + 2), context(line + 3)),
  )

  private fun file(path: String) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(listOf(replaceHunk(11), replaceHunk(80))))

  private class FakeSource(private val block: () -> Flow<FileDiff>) : DiffSource {
    override suspend fun resolveBase(repo: RepoPath, spec: BaseSpec): ResolvedBase = ResolvedBase.Commit("HEAD", "h", "h")
    override fun load(repo: RepoPath, base: ResolvedBase): Flow<FileDiff> = block()
    override suspend fun head(repo: RepoPath): HeadRef? = null
    override suspend fun branches(repo: RepoPath): List<BranchRef> = emptyList()
    override suspend fun root(repo: RepoPath): RepoPath = repo
  }

  private class FakeWorkingTree(val entries: MutableMap<String, UntrackedEntry>) : WorkingTreeReader {
    val reads = mutableListOf<Pair<String, Long>>()
    val contexts = mutableListOf<String?>()

    override suspend fun read(root: String, relativePath: String, maxBytes: Long): UntrackedEntry {
      reads += relativePath to maxBytes
      contexts += currentCoroutineContext()[CoroutineName]?.name
      return entries[relativePath] ?: UntrackedEntry.Unreadable
    }
  }

  private fun regular(text: List<String>) = UntrackedEntry.Regular(text.joinToString("\n", postfix = "\n").encodeToByteArray())

  private fun TestScope.stateWith(tree: FakeWorkingTree, vararg files: FileDiff) = DiffViewerState(FakeSource { flow { files.forEach { emit(it) } } }, this, 16, workingTree = tree)

  private fun ViewerUiState.lineTexts(fileIndex: Int) = rows.filterIsInstance<DiffRow.Line>().filter { it.fileIndex == fileIndex }.map { it.line.text }

  private fun ViewerUiState.gaps(fileIndex: Int) = rows.filterIsInstance<DiffRow.HunkHeader>().filter { it.fileIndex == fileIndex }.map { it.gap }

  @Test
  fun expandRevealsLinesAndBumpsContentId() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("f.kt" to regular(lines)))
    val state = stateWith(tree, file("f.kt"))
    state.open(repo)
    advanceUntilIdle()
    val before = state.uiState.value
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    val after = state.uiState.value
    assertTrue("L34" in after.lineTexts(0))
    assertTrue("L35" !in after.lineTexts(0))
    assertTrue(after.contentId > before.contentId)
    assertTrue(after.rowsGeneration > before.rowsGeneration)
    assertEquals(listOf("f.kt" to EXPAND_SIZE_LIMIT), tree.reads)
  }

  @Test
  fun queuedExpansionsAllApply() = runTest(StandardTestDispatcher()) {
    val state = stateWith(FakeWorkingTree(mutableMapOf("f.kt" to regular(lines))), file("f.kt"))
    state.open(repo)
    advanceUntilIdle()
    repeat(3) { state.expandContext(0, 1, ExpandDirection.Down) }
    advanceUntilIdle()
    assertEquals(listOf(GapView(0, 7, canUp = true, canDown = false), GapView(1, 2, canUp = true, canDown = true)), state.uiState.value.gaps(0))
  }

  @Test
  fun tooLargeSetsErrorAndKeepsRows() = runTest(StandardTestDispatcher()) {
    val state = stateWith(FakeWorkingTree(mutableMapOf("f.kt" to UntrackedEntry.TooLarge)), file("f.kt"))
    state.open(repo)
    advanceUntilIdle()
    val rows = state.uiState.value.rows
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    assertSame(rows, state.uiState.value.rows)
    assertEquals(listOf(ExpansionError.TooLarge), state.uiState.value.expansionErrors.values.toList())
  }

  @Test
  fun unreadableSetsError() = runTest(StandardTestDispatcher()) {
    val state = stateWith(FakeWorkingTree(mutableMapOf()), file("f.kt"))
    state.open(repo)
    advanceUntilIdle()
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    assertEquals(listOf(ExpansionError.Unreadable), state.uiState.value.expansionErrors.values.toList())
  }

  @Test
  fun mismatchSetsErrorAndKeepsRows() = runTest(StandardTestDispatcher()) {
    val changed = lines.toMutableList().also { it[8] = "edited" }
    val state = stateWith(FakeWorkingTree(mutableMapOf("f.kt" to regular(changed))), file("f.kt"))
    state.open(repo)
    advanceUntilIdle()
    val rows = state.uiState.value.rows
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    assertSame(rows, state.uiState.value.rows)
    assertEquals(listOf(ExpansionError.Stale), state.uiState.value.expansionErrors.values.toList())
  }

  @Test
  fun successClearsError() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("f.kt" to UntrackedEntry.TooLarge))
    val state = stateWith(tree, file("f.kt"))
    state.open(repo)
    advanceUntilIdle()
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    tree.entries["f.kt"] = regular(lines)
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    assertEquals(emptyMap(), state.uiState.value.expansionErrors)
  }

  @Test
  fun expandWhileLoadingIsIgnored() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("f.kt" to regular(lines)))
    val state = DiffViewerState(
      FakeSource {
        flow {
          emit(file("f.kt"))
          awaitCancellation()
        }
      },
      backgroundScope,
      16,
      workingTree = tree,
    )
    state.open(repo)
    advanceTimeBy(100)
    runCurrent()
    assertEquals(1, state.uiState.value.files.size)
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceTimeBy(100)
    runCurrent()
    assertEquals(LoadStatus.Loading, state.uiState.value.status)
    assertEquals(emptyList(), tree.reads)
  }

  @Test
  fun otherFilesRowsAreReused() = runTest(StandardTestDispatcher()) {
    val state = stateWith(FakeWorkingTree(mutableMapOf("a.kt" to regular(lines))), file("a.kt"), file("b.kt"))
    state.open(repo)
    advanceUntilIdle()
    val before = state.uiState.value
    val bRows = before.rows.subList(before.fileHeaderRowIndex[1], before.rows.size)
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    val after = state.uiState.value
    val shift = after.fileHeaderRowIndex[1] - before.fileHeaderRowIndex[1]
    assertEquals(20, shift)
    bRows.forEachIndexed { offset, row -> assertSame(row, after.rows[after.fileHeaderRowIndex[1] + offset]) }
    assertEquals(after.split.size - 1, after.split.splitIndexOf(after.rows.lastIndex))
  }

  @Test
  fun expansionReplacesHighlightSlot() = runTest(StandardTestDispatcher()) {
    val highlighter = FakeHighlighter()
    val text = lines.joinToString("\n", postfix = "\n")
    val contentSource = FakeContentSource(mapOf(("f.kt" to Side.Old) to text, ("f.kt" to Side.New) to text))
    val state = DiffViewerState(
      FakeSource { flow { emit(file("f.kt")) } },
      backgroundScope,
      16,
      pipeline(contentSource, highlighter),
      workingTree = FakeWorkingTree(mutableMapOf("f.kt" to regular(lines))),
    )
    state.open(repo)
    advanceTimeBy(100)
    runCurrent()
    val slot = state.uiState.value.highlights[0]
    val calls = highlighter.calls.size
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceTimeBy(100)
    runCurrent()
    assertNotSame(slot, state.uiState.value.highlights[0])
    assertTrue(highlighter.calls.size > calls)
  }

  private fun TestScope.recordedStates(state: DiffViewerState): MutableList<ViewerUiState> {
    val states = mutableListOf<ViewerUiState>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.uiState.collect { states += it } }
    return states
  }

  @Test
  fun reloadKeepsExpansionOfUnchangedFile() = runTest(StandardTestDispatcher()) {
    val state = stateWith(FakeWorkingTree(mutableMapOf("f.kt" to regular(lines))), file("f.kt"))
    state.open(repo)
    advanceUntilIdle()
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    val states = recordedStates(state)
    val reloadId = state.reload()!!
    advanceUntilIdle()
    val done = states.filter { it.loadId == reloadId && it.status == LoadStatus.Done }
    assertTrue(done.isNotEmpty())
    done.forEach { assertTrue("L20" in it.lineTexts(0)) }
    assertEquals(1, state.uiState.value.expansions.size)
  }

  @Test
  fun reloadDropsExpansionOfChangedFile() = runTest(StandardTestDispatcher()) {
    var call = 0
    val changed = FileDiff("f.kt", "f.kt", ChangeType.Modified, false, DiffContent.Text(listOf(replaceHunk(11), replaceHunk(81))))
    val state = DiffViewerState(FakeSource { flow { emit(if (call++ == 0) file("f.kt") else changed) } }, this, 16, workingTree = FakeWorkingTree(mutableMapOf("f.kt" to regular(lines))))
    state.open(repo)
    advanceUntilIdle()
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    state.reload()
    advanceUntilIdle()
    assertTrue("L20" !in state.uiState.value.lineTexts(0))
    assertEquals(emptyMap(), state.uiState.value.expansions)
  }

  @Test
  fun keptExpansionWhoseTextChangedShowsError() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("f.kt" to regular(lines)))
    val state = stateWith(tree, file("f.kt"))
    state.open(repo)
    advanceUntilIdle()
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    tree.entries["f.kt"] = regular(lines.toMutableList().also { it[8] = "edited" })
    state.reload()
    advanceUntilIdle()
    assertTrue("L20" !in state.uiState.value.lineTexts(0))
    assertEquals(listOf(ExpansionError.Stale), state.uiState.value.expansionErrors.values.toList())
  }

  @Test
  fun threadOnRevealedLineStaysPlacedAfterReload() = runTest(StandardTestDispatcher()) {
    val state = stateWith(FakeWorkingTree(mutableMapOf("f.kt" to regular(lines))), file("f.kt"))
    val review = ReviewState(backgroundScope, StandardTestDispatcher(testScheduler)) { Instant.fromEpochSeconds(0) }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.uiState.collect(review::onViewerState) }
    state.open(repo)
    advanceUntilIdle()
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    review.startDraft(DraftTarget.NewThread(Anchor.Lines("f.kt", ReviewSide.New, 20, 20, listOf("L20")), Placement.OnLine(LineLocation(0))))
    review.updateDraft("why")
    review.submitDraft(DraftMode.Single)
    val reloadId = state.reload()!!
    advanceUntilIdle()
    val settlement = review.awaitSettled(reloadId)
    assertEquals(0, settlement.outdated)
    val thread = review.snapshot.value.threads.single()
    assertFalse(thread.outdated)
    assertEquals(listOf(thread), review.snapshot.value.placements.byLine[sidePosition(0, ReviewSide.New, 20)])
  }

  @Test
  fun expansionWorkRunsInLoadContext() = runTest(StandardTestDispatcher()) {
    val tree = FakeWorkingTree(mutableMapOf("f.kt" to regular(lines)))
    val state = DiffViewerState(FakeSource { flow { emit(file("f.kt")) } }, this, 16, loadContext = CoroutineName("load"), workingTree = tree)
    state.open(repo)
    advanceUntilIdle()
    state.expandContext(0, 1, ExpandDirection.Down)
    advanceUntilIdle()
    state.reload()
    advanceUntilIdle()
    assertEquals(listOf<String?>("load", "load"), tree.contexts)
  }

  @Test
  fun repeatedExpandAllIsANoOp() = runTest(StandardTestDispatcher()) {
    val state = stateWith(FakeWorkingTree(mutableMapOf("f.kt" to regular(lines))), file("f.kt"))
    state.open(repo)
    advanceUntilIdle()
    state.expandContext(0, 1, ExpandDirection.All)
    advanceUntilIdle()
    val before = state.uiState.value
    state.expandContext(0, 1, ExpandDirection.All)
    advanceUntilIdle()
    assertSame(before, state.uiState.value)
  }
}

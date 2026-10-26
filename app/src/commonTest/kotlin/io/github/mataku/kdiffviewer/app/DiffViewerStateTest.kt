package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.app.highlight.FakeContentSource
import io.github.mataku.kdiffviewer.app.highlight.FakeHighlighter
import io.github.mataku.kdiffviewer.app.highlight.HighlightSlot
import io.github.mataku.kdiffviewer.app.highlight.line
import io.github.mataku.kdiffviewer.app.highlight.pipeline
import io.github.mataku.kdiffviewer.app.highlight.textFile
import io.github.mataku.kdiffviewer.core.git.DiffLoadException
import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.Side
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiffViewerStateTest {
  private val repo = RepoPath("/repo")

  private fun file(path: String) = FileDiff(path, path, ChangeType.Modified, false, DiffContent.Text(emptyList()))

  private fun fileWithLines(path: String, lineCount: Int) = FileDiff(
    path,
    path,
    ChangeType.Modified,
    false,
    DiffContent.Text(
      listOf(
        Hunk(
          1,
          lineCount,
          1,
          lineCount,
          "",
          (1..lineCount).map { DiffLine(LineKind.Context, "line $it", it, it) },
        ),
      ),
    ),
  )

  private class FakeSource(
    private val heads: List<HeadRef?> = emptyList(),
    private val block: (Int) -> Flow<FileDiff>,
  ) : DiffSource {
    var calls = 0
    private var headCalls = 0
    override fun load(repo: RepoPath): Flow<FileDiff> = block(calls++)
    override suspend fun head(repo: RepoPath): HeadRef? = heads.getOrNull(headCalls++)
  }

  private fun TestScope.stateFor(source: DiffSource?) = DiffViewerState(source, this, 16)

  @Test
  fun openEmitsLoadingThenDone() = runTest(StandardTestDispatcher()) {
    val files = listOf(file("a"), file("b"))
    val state = stateFor(
      FakeSource {
        flow {
          delay(1)
          files.forEach { emit(it) }
        }
      },
    )
    state.open(repo)
    assertEquals(LoadStatus.Loading, state.uiState.value.status)
    assertEquals(repo, state.uiState.value.repo)
    advanceUntilIdle()
    val ui = state.uiState.value
    assertEquals(LoadStatus.Done, ui.status)
    assertEquals(files, ui.files)
    assertEquals(flattenRows(files), ui.rows)
    assertEquals(buildFileTree(files), ui.tree)
  }

  @Test
  fun openPublishesHead() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource(listOf(HeadRef.Branch("main"))) { flow {} })
    state.open(repo)
    advanceUntilIdle()
    assertEquals(HeadRef.Branch("main"), state.uiState.value.head)
  }

  @Test
  fun reloadRefreshesHead() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource(listOf(HeadRef.Branch("main"), HeadRef.Branch("feature/x"))) { flow {} })
    state.open(repo)
    advanceUntilIdle()
    state.reload()
    advanceUntilIdle()
    assertEquals(HeadRef.Branch("feature/x"), state.uiState.value.head)
  }

  @Test
  fun reloadKeepsHeadWhileLoading() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource(listOf(HeadRef.Branch("main"), HeadRef.Branch("main"))) { flow {} })
    state.open(repo)
    advanceUntilIdle()
    state.reload()
    assertEquals(LoadStatus.Loading, state.uiState.value.status)
    assertEquals(HeadRef.Branch("main"), state.uiState.value.head)
  }

  @Test
  fun openingAnotherRepoClearsHead() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource(listOf(HeadRef.Branch("main"))) { flow {} })
    state.open(repo)
    advanceUntilIdle()
    state.open(RepoPath("/other"))
    assertNull(state.uiState.value.head)
  }

  @Test
  fun headFailureDoesNotFailTheLoad() = runTest(StandardTestDispatcher()) {
    val source = object : DiffSource {
      override fun load(repo: RepoPath): Flow<FileDiff> = flow { emit(file("a")) }
      override suspend fun head(repo: RepoPath): HeadRef? = throw IllegalStateException("boom")
    }
    val state = stateFor(source)
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Done, state.uiState.value.status)
    assertNull(state.uiState.value.head)
  }

  @Test
  fun reloadKeepsPreviousContentUntilLoadCompletes() = runTest(StandardTestDispatcher()) {
    val state = stateFor(
      FakeSource { call ->
        if (call == 0) {
          flow { emit(file("a")) }
        } else {
          flow {
            delay(100)
            emit(file("a"))
            delay(100)
            emit(file("b"))
          }
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    val firstLoadId = state.uiState.value.loadId
    state.reload()
    advanceTimeBy(150)
    runCurrent()
    assertEquals(listOf(file("a")), state.uiState.value.files)
    assertEquals(LoadStatus.Loading, state.uiState.value.status)
    assertEquals(firstLoadId, state.uiState.value.loadId)
    advanceUntilIdle()
    assertEquals(listOf(file("a"), file("b")), state.uiState.value.files)
    assertEquals(LoadStatus.Done, state.uiState.value.status)
    assertTrue(state.uiState.value.loadId > firstLoadId)
  }

  @Test
  fun slowReloadSwitchesToStreamingAfterHoldWindow() = runTest(StandardTestDispatcher()) {
    val state = stateFor(
      FakeSource { call ->
        if (call == 0) {
          flow { emit(file("old")) }
        } else {
          flow {
            delay(100)
            emit(file("new"))
            delay(1_000)
          }
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    val firstLoadId = state.uiState.value.loadId
    state.reload()
    advanceTimeBy(350)
    runCurrent()
    assertEquals(listOf(file("new")), state.uiState.value.files)
    assertEquals(LoadStatus.Loading, state.uiState.value.status)
    assertTrue(state.uiState.value.loadId > firstLoadId)
  }

  @Test
  fun openingAnotherRepoClearsContentImmediately() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { emit(file("a")) } })
    state.open(repo)
    advanceUntilIdle()
    state.open(RepoPath("/other"))
    assertEquals(emptyList(), state.uiState.value.files)
    assertEquals(emptyList(), state.uiState.value.tree)
  }

  @Test
  fun filesArrivingWithinWindowAreBatched() = runTest(StandardTestDispatcher()) {
    val files = listOf(file("a"), file("b"), file("c"))
    val state = stateFor(
      FakeSource {
        flow {
          files.forEach {
            delay(1)
            emit(it)
          }
        }
      },
    )
    val sizes = mutableListOf<Int>()
    backgroundScope.launch { state.uiState.collect { sizes += it.files.size } }
    state.open(repo)
    advanceUntilIdle()
    assertTrue(sizes.filter { it > 0 }.distinct().size < 3, "sizes=$sizes")
    assertEquals(3, state.uiState.value.files.size)
  }

  @Test
  fun rowsAreAppendedIncrementally() = runTest(StandardTestDispatcher()) {
    val files = listOf(fileWithLines("a", 2), fileWithLines("b", 3), fileWithLines("c", 1))
    val state = stateFor(
      FakeSource {
        flow {
          emit(files[0])
          emit(files[1])
          delay(100)
          emit(files[2])
        }
      },
    )
    state.open(repo)
    advanceTimeBy(50)
    runCurrent()
    assertEquals(2, state.uiState.value.files.size)
    advanceUntilIdle()
    val ui = state.uiState.value
    assertEquals(flattenRows(files), ui.rows)
    assertEquals(3, ui.fileHeaderRowIndex.size)
    assertEquals(listOf(0, 4, 9), ui.fileHeaderRowIndex)
    ui.fileHeaderRowIndex.forEachIndexed { fileIndex, rowIndex ->
      val row = ui.rows[rowIndex]
      assertTrue(row is DiffRow.FileHeader && row.fileIndex == fileIndex)
    }
  }

  @Test
  fun stalledSourceStillPublishesArrivedFileWithinWindow() = runTest(StandardTestDispatcher()) {
    val state = DiffViewerState(
      FakeSource {
        flow {
          emit(file("a"))
          awaitCancellation()
        }
      },
      backgroundScope,
      16,
    )
    state.open(repo)
    advanceTimeBy(17)
    runCurrent()
    assertEquals(1, state.uiState.value.files.size)
    assertEquals(LoadStatus.Loading, state.uiState.value.status)
  }

  @Test
  fun reloadCancelsInFlightLoad() = runTest(StandardTestDispatcher()) {
    var firstCancelled = false
    val second = listOf(file("x"), file("y"))
    val state = stateFor(
      FakeSource { call ->
        if (call == 0) {
          flow {
            try {
              emit(file("old"))
              awaitCancellation()
            } catch (e: CancellationException) {
              firstCancelled = true
              throw e
            }
          }
        } else {
          flow {
            delay(1)
            second.forEach { emit(it) }
          }
        }
      },
    )
    state.open(repo)
    advanceTimeBy(20)
    runCurrent()
    assertEquals(1, state.uiState.value.files.size)
    state.reload()
    assertEquals(listOf(file("old")), state.uiState.value.files)
    assertEquals(LoadStatus.Loading, state.uiState.value.status)
    advanceUntilIdle()
    assertTrue(firstCancelled)
    assertEquals(LoadStatus.Done, state.uiState.value.status)
    assertEquals(second, state.uiState.value.files)
  }

  @Test
  fun cancelledLoadThatKeepsEmittingDoesNotLeakIntoReload() = runTest(StandardTestDispatcher()) {
    val stubborn = object : Flow<FileDiff> {
      override suspend fun collect(collector: FlowCollector<FileDiff>) {
        repeat(50) { index ->
          runCatching { collector.emit(file("old$index")) }
          withContext(NonCancellable) { delay(5) }
        }
      }
    }
    val second = listOf(file("x"), file("y"))
    val state = stateFor(
      FakeSource { call ->
        if (call == 0) {
          stubborn
        } else {
          flow {
            delay(1)
            second.forEach { emit(it) }
          }
        }
      },
    )
    state.open(repo)
    advanceTimeBy(20)
    runCurrent()
    assertTrue(state.uiState.value.files.isNotEmpty())
    state.reload()
    advanceUntilIdle()
    assertEquals(LoadStatus.Done, state.uiState.value.status)
    assertEquals(second, state.uiState.value.files)
  }

  @Test
  fun unexpectedCancellationFromSourceFailsInsteadOfHanging() = runTest(StandardTestDispatcher()) {
    val state = stateFor(
      FakeSource {
        flow {
          emit(file("a"))
          throw CancellationException("stray")
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.GitFailed("stray")), state.uiState.value.status)
    assertEquals(1, state.uiState.value.files.size)
  }

  @Test
  fun treeIsCompleteWhenLoadFinishesAfterManyBatches() = runTest(StandardTestDispatcher()) {
    val files = (0 until 50).map { file("dir${it % 3}/f$it") }
    val state = stateFor(
      FakeSource {
        flow {
          files.forEach {
            delay(20)
            emit(it)
          }
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Done, state.uiState.value.status)
    assertEquals(buildFileTree(files), state.uiState.value.tree)
  }

  @Test
  fun treeIsCompleteWhenLoadFails() = runTest(StandardTestDispatcher()) {
    val files = (0 until 10).map { file("f$it") }
    val state = stateFor(
      FakeSource {
        flow {
          files.forEach {
            delay(20)
            emit(it)
          }
          throw DiffLoadException.GitFailed(listOf("ls-files"), 1, "bad")
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    assertEquals(buildFileTree(files), state.uiState.value.tree)
  }

  @Test
  fun failureFlushesFilesBufferedInWindow() = runTest(StandardTestDispatcher()) {
    val state = stateFor(
      FakeSource {
        flow {
          emit(file("a"))
          emit(file("b"))
          throw DiffLoadException.GitFailed(listOf("ls-files"), 1, "bad")
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.GitFailed("bad", 1)), state.uiState.value.status)
    assertEquals(2, state.uiState.value.files.size)
  }

  @Test
  fun loadIdChangesWhenDisplayedContentIsReplaced() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { } })
    state.open(repo)
    val first = state.uiState.value.loadId
    advanceUntilIdle()
    assertEquals(first, state.uiState.value.loadId)
    state.reload()
    assertEquals(first, state.uiState.value.loadId)
    advanceUntilIdle()
    val second = state.uiState.value.loadId
    state.open(RepoPath("/other"))
    val third = state.uiState.value.loadId
    assertTrue(first < second && second < third, "ids=$first,$second,$third")
  }

  @Test
  fun notARepositoryMapsToError() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { throw DiffLoadException.NotARepository("/x") } })
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.NotARepository("/x")), state.uiState.value.status)
  }

  @Test
  fun gitFailedMapsToError() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { throw DiffLoadException.GitFailed(listOf("diff"), 128, "fatal") } })
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.GitFailed("fatal", 128)), state.uiState.value.status)
  }

  @Test
  fun otherExceptionsMapToGitFailedWithMessage() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { throw IllegalStateException("boom") } })
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.GitFailed("boom")), state.uiState.value.status)
  }

  @Test
  fun failureKeepsFilesAlreadyPublished() = runTest(StandardTestDispatcher()) {
    val state = stateFor(
      FakeSource {
        flow {
          emit(file("a"))
          delay(100)
          throw DiffLoadException.GitFailed(listOf("ls-files"), 1, "bad")
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.GitFailed("bad", 1)), state.uiState.value.status)
    assertEquals(1, state.uiState.value.files.size)
  }

  @Test
  fun missingGitMapsToError() = runTest(StandardTestDispatcher()) {
    val state = stateFor(null)
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.GitNotFound), state.uiState.value.status)
    assertEquals(repo, state.uiState.value.repo)
  }

  @Test
  fun reloadWithoutRepoDoesNothing() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { } })
    state.reload()
    advanceUntilIdle()
    assertEquals(LoadStatus.Idle, state.uiState.value.status)
  }

  @Test
  fun reloadReturnsNewLoadId() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { } })
    val opened = state.open(repo)
    advanceUntilIdle()
    val reloaded = state.reload()
    assertTrue(reloaded != null && reloaded > opened)
    advanceUntilIdle()
    assertEquals(reloaded, state.uiState.value.loadId)
  }

  @Test
  fun reloadWithoutRepoReturnsNull() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { } })
    assertNull(state.reload())
  }

  @Test
  fun noChangesIsDoneWithEmptyFiles() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { } })
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Done, state.uiState.value.status)
    assertEquals(emptyList(), state.uiState.value.files)
  }

  private fun ktFile(name: String) = textFile(name, name, listOf(line(LineKind.Context, name, 1, 1)))

  private val ktFiles = listOf("a.kt", "b.kt", "c.kt").map { ktFile(it) }

  private val ktTexts = ktFiles.flatMap { file ->
    listOf((file.oldPath!! to Side.Old) to file.oldPath!!, (file.newPath!! to Side.New) to file.newPath!!)
  }.toMap()

  private fun TestScope.highlightingState(source: DiffSource, highlighter: FakeHighlighter, contentSource: FakeContentSource) = DiffViewerState(source, backgroundScope, 16, pipeline(contentSource, highlighter))

  @Test
  fun everyFileGetsASlot() = runTest(StandardTestDispatcher()) {
    val state = highlightingState(FakeSource { flow { ktFiles.forEach { emit(it) } } }, FakeHighlighter(), FakeContentSource(ktTexts))
    state.open(repo)
    advanceTimeBy(100)
    runCurrent()
    assertEquals(3, state.uiState.value.highlights.size)
  }

  @Test
  fun publishedFilesAreSubmitted() = runTest(StandardTestDispatcher()) {
    val highlighter = FakeHighlighter()
    val state = highlightingState(FakeSource { flow { ktFiles.forEach { emit(it) } } }, highlighter, FakeContentSource(ktTexts))
    state.open(repo)
    advanceTimeBy(100)
    runCurrent()
    assertEquals(listOf("a.kt", "b.kt", "c.kt"), highlighter.calls)
    state.uiState.value.highlights.forEach { assertNotNull(it.value) }
  }

  @Test
  fun reloadCancelsPreviousRun() = runTest(StandardTestDispatcher()) {
    val highlighter = FakeHighlighter()
    val contentSource = FakeContentSource(ktTexts)
    val state = highlightingState(
      FakeSource { call -> if (call == 0) flow { emit(ktFiles[0]) } else flow { awaitCancellation() } },
      highlighter,
      contentSource,
    )
    val firstSlots = mutableListOf<HighlightSlot>()
    highlighter.onCall = {
      if (it == 1) {
        firstSlots += state.uiState.value.highlights
        state.open(repo)
      }
    }
    state.open(repo)
    advanceTimeBy(17)
    runCurrent()
    assertTrue(contentSource.closed)
    assertEquals(1, firstSlots.size)
    assertNull(firstSlots[0].value)
    assertEquals(firstSlots, state.uiState.value.highlights)
  }

  @Test
  fun setBackendReloadsOpenRepositoryWithNewSource() = runTest(StandardTestDispatcher()) {
    val a = FakeSource { flow { emit(file("a")) } }
    val b = FakeSource { flow { emit(file("b")) } }
    val state = stateFor(a)
    state.open(repo)
    advanceUntilIdle()
    state.setBackend(b, null)
    advanceUntilIdle()
    assertEquals(1, a.calls)
    assertEquals(1, b.calls)
    assertEquals(LoadStatus.Done, state.uiState.value.status)
    assertEquals(listOf(file("b")), state.uiState.value.files)
  }

  @Test
  fun setBackendCancelsInFlightLoad() = runTest(StandardTestDispatcher()) {
    var aCancelled = false
    val a = FakeSource {
      flow {
        try {
          awaitCancellation()
        } catch (e: CancellationException) {
          aCancelled = true
          throw e
        }
      }
    }
    val b = FakeSource { flow { emit(file("b")) } }
    val state = stateFor(a)
    state.open(repo)
    advanceTimeBy(20)
    runCurrent()
    state.setBackend(b, null)
    advanceUntilIdle()
    assertTrue(aCancelled)
    assertEquals(LoadStatus.Done, state.uiState.value.status)
    assertEquals(listOf(file("b")), state.uiState.value.files)
  }

  @Test
  fun setBackendWithoutRepositoryDoesNotLoad() = runTest(StandardTestDispatcher()) {
    val b = FakeSource { flow { emit(file("b")) } }
    val state = stateFor(null)
    state.setBackend(b, null)
    advanceUntilIdle()
    assertEquals(0, b.calls)
    assertEquals(LoadStatus.Idle, state.uiState.value.status)
  }

  @Test
  fun missingSourceUsesConfiguredError() = runTest(StandardTestDispatcher()) {
    val state = DiffViewerState(null, this, 16, missingSourceError = ViewerError.GitUnavailable("/gone"))
    state.open(repo)
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.GitUnavailable("/gone")), state.uiState.value.status)
  }

  @Test
  fun setBackendNullWithErrorShowsThatError() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { emit(file("a")) } })
    state.open(repo)
    advanceUntilIdle()
    state.setBackend(null, null, ViewerError.GitUnavailable("/x"))
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.GitUnavailable("/x")), state.uiState.value.status)
  }

  @Test
  fun setBackendToNullShowsGitNotFound() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { emit(file("a")) } })
    state.open(repo)
    advanceUntilIdle()
    state.setBackend(null, null)
    advanceUntilIdle()
    assertEquals(LoadStatus.Failed(ViewerError.GitNotFound), state.uiState.value.status)
  }

  @Test
  fun noPipelineMeansNoHighlighting() = runTest(StandardTestDispatcher()) {
    val state = DiffViewerState(FakeSource { flow { ktFiles.forEach { emit(it) } } }, this, 16, null)
    state.open(repo)
    advanceUntilIdle()
    val ui = state.uiState.value
    assertEquals(ui.files.size, ui.highlights.size)
    ui.highlights.forEach { assertNull(it.value) }
  }

  private fun textFileWith(path: String, text: String) = FileDiff(
    path,
    path,
    ChangeType.Modified,
    false,
    DiffContent.Text(listOf(Hunk(1, 1, 1, 1, "", listOf(DiffLine(LineKind.Context, text, 1, 1))))),
  )

  @Test
  fun loadAppendsOneFingerprintPerFile() = runTest(StandardTestDispatcher()) {
    val files = listOf(fileWithLines("a", 2), fileWithLines("b", 3))
    val state = stateFor(FakeSource { flow { files.forEach { emit(it) } } })
    state.open(repo)
    advanceUntilIdle()
    assertEquals(files.map { it.fingerprint() }, state.uiState.value.fingerprints)
  }

  @Test
  fun toggleCollapsedCollapsesAndExpands() = runTest(StandardTestDispatcher()) {
    val a = fileWithLines("a", 2)
    val state = stateFor(FakeSource { flow { emit(a) } })
    state.open(repo)
    advanceUntilIdle()
    state.toggleCollapsed(0)
    assertTrue(state.uiState.value.isCollapsed(0))
    assertEquals(setOf(CollapsedFile("a", a.fingerprint())), state.uiState.value.collapsed)
    state.toggleCollapsed(0)
    assertFalse(state.uiState.value.isCollapsed(0))
    assertEquals(emptySet(), state.uiState.value.collapsed)
  }

  @Test
  fun expandOnExpandedFileEmitsNothing() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { emit(fileWithLines("a", 2)) } })
    state.open(repo)
    advanceUntilIdle()
    val before = state.uiState.value
    state.expand(0)
    assertSame(before, state.uiState.value)
  }

  @Test
  fun collapseSurvivesLaterBatches() = runTest(StandardTestDispatcher()) {
    val state = stateFor(
      FakeSource {
        flow {
          emit(fileWithLines("a", 2))
          delay(100)
          emit(fileWithLines("b", 2))
        }
      },
    )
    state.open(repo)
    advanceTimeBy(50)
    runCurrent()
    assertEquals(1, state.uiState.value.files.size)
    state.toggleCollapsed(0)
    advanceUntilIdle()
    assertEquals(LoadStatus.Done, state.uiState.value.status)
    assertTrue(state.uiState.value.isCollapsed(0))
    assertFalse(state.uiState.value.isCollapsed(1))
  }

  @Test
  fun reloadWithSameDiffKeepsFileCollapsed() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { emit(textFileWith("a", "same")) } })
    state.open(repo)
    advanceUntilIdle()
    state.toggleCollapsed(0)
    state.reload()
    advanceUntilIdle()
    assertTrue(state.uiState.value.isCollapsed(0))
  }

  @Test
  fun reloadWithChangedDiffExpandsAndPrunes() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { call -> flow { emit(textFileWith("a", "v$call")) } })
    state.open(repo)
    advanceUntilIdle()
    state.toggleCollapsed(0)
    state.reload()
    advanceUntilIdle()
    assertFalse(state.uiState.value.isCollapsed(0))
    assertEquals(emptySet(), state.uiState.value.collapsed)
  }

  @Test
  fun reloadPrunesMissingPaths() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { call -> flow { emit(fileWithLines(if (call == 0) "a" else "b", 2)) } })
    state.open(repo)
    advanceUntilIdle()
    state.toggleCollapsed(0)
    state.reload()
    advanceUntilIdle()
    assertEquals(emptySet(), state.uiState.value.collapsed)
  }

  @Test
  fun previousContentStaysCollapsedDuringReloadHold() = runTest(StandardTestDispatcher()) {
    val state = stateFor(
      FakeSource { call ->
        if (call == 0) {
          flow { emit(fileWithLines("a", 2)) }
        } else {
          flow {
            delay(200)
            emit(fileWithLines("a", 2))
          }
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    state.toggleCollapsed(0)
    state.reload()
    advanceTimeBy(100)
    runCurrent()
    assertEquals(LoadStatus.Loading, state.uiState.value.status)
    assertTrue(state.uiState.value.isCollapsed(0))
  }

  @Test
  fun openingAnotherRepoClearsCollapsed() = runTest(StandardTestDispatcher()) {
    val state = stateFor(FakeSource { flow { emit(fileWithLines("a", 2)) } })
    state.open(repo)
    advanceUntilIdle()
    state.toggleCollapsed(0)
    state.open(RepoPath("/other"))
    assertEquals(emptySet(), state.uiState.value.collapsed)
  }

  @Test
  fun samePathWithDifferentDiffsCollapsesIndependently() = runTest(StandardTestDispatcher()) {
    val deleted = FileDiff(
      "x",
      null,
      ChangeType.Deleted,
      false,
      DiffContent.Text(listOf(Hunk(1, 1, 0, 0, "", listOf(DiffLine(LineKind.Removed, "old", 1, null))))),
    )
    val untracked = FileDiff(
      null,
      "x",
      ChangeType.Added,
      true,
      DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", listOf(DiffLine(LineKind.Added, "new", null, 1))))),
    )
    val state = stateFor(
      FakeSource {
        flow {
          emit(deleted)
          emit(untracked)
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    state.toggleCollapsed(1)
    assertTrue(state.uiState.value.isCollapsed(1))
    assertFalse(state.uiState.value.isCollapsed(0))
  }

  @Test
  fun bothFilesWithTheSamePathCanBeCollapsed() = runTest(StandardTestDispatcher()) {
    val deleted = FileDiff(
      "x",
      null,
      ChangeType.Deleted,
      false,
      DiffContent.Text(listOf(Hunk(1, 1, 0, 0, "", listOf(DiffLine(LineKind.Removed, "old", 1, null))))),
    )
    val untracked = FileDiff(
      null,
      "x",
      ChangeType.Added,
      true,
      DiffContent.Text(listOf(Hunk(0, 0, 1, 1, "", listOf(DiffLine(LineKind.Added, "new", null, 1))))),
    )
    val state = stateFor(
      FakeSource {
        flow {
          emit(deleted)
          emit(untracked)
        }
      },
    )
    state.open(repo)
    advanceUntilIdle()
    state.toggleCollapsed(0)
    state.toggleCollapsed(1)
    assertTrue(state.uiState.value.isCollapsed(0))
    assertTrue(state.uiState.value.isCollapsed(1))
    state.reload()
    advanceUntilIdle()
    assertTrue(state.uiState.value.isCollapsed(0))
    assertTrue(state.uiState.value.isCollapsed(1))
  }
}

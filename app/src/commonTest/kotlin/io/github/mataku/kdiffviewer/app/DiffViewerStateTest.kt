package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.git.DiffLoadException
import io.github.mataku.kdiffviewer.core.git.DiffSource
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.DiffLine
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.LineKind
import io.github.mataku.kdiffviewer.core.model.RepoPath
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
                    1, lineCount, 1, lineCount, "",
                    (1..lineCount).map { DiffLine(LineKind.Context, "line $it", it, it) },
                ),
            ),
        ),
    )

    private class FakeSource(private val block: (Int) -> Flow<FileDiff>) : DiffSource {
        var calls = 0
        override fun load(repo: RepoPath): Flow<FileDiff> = block(calls++)
    }

    private fun TestScope.stateFor(source: DiffSource?) =
        DiffViewerState(source, this, 16)

    @Test
    fun openEmitsLoadingThenDone() = runTest(StandardTestDispatcher()) {
        val files = listOf(file("a"), file("b"))
        val state = stateFor(FakeSource { flow { delay(1); files.forEach { emit(it) } } })
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
    fun filesArrivingWithinWindowAreBatched() = runTest(StandardTestDispatcher()) {
        val files = listOf(file("a"), file("b"), file("c"))
        val state = stateFor(FakeSource { flow { files.forEach { delay(1); emit(it) } } })
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
                    emit(files[0]); emit(files[1])
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
        val state = DiffViewerState(FakeSource { flow { emit(file("a")); awaitCancellation() } }, backgroundScope, 16)
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
                    flow { delay(1); second.forEach { emit(it) } }
                }
            },
        )
        state.open(repo)
        advanceTimeBy(20)
        runCurrent()
        assertEquals(1, state.uiState.value.files.size)
        state.reload()
        assertEquals(emptyList(), state.uiState.value.files)
        assertEquals(emptyList(), state.uiState.value.rows)
        assertEquals(emptyList(), state.uiState.value.tree)
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
                if (call == 0) stubborn else flow { delay(1); second.forEach { emit(it) } }
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
        val state = stateFor(FakeSource { flow { emit(file("a")); throw CancellationException("stray") } })
        state.open(repo)
        advanceUntilIdle()
        assertEquals(LoadStatus.Failed(ViewerError.GitFailed("stray")), state.uiState.value.status)
        assertEquals(1, state.uiState.value.files.size)
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
    fun everyOpenAndReloadGetsANewLoadId() = runTest(StandardTestDispatcher()) {
        val state = stateFor(FakeSource { flow { } })
        state.open(repo)
        val first = state.uiState.value.loadId
        advanceUntilIdle()
        assertEquals(first, state.uiState.value.loadId)
        state.reload()
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
    fun noChangesIsDoneWithEmptyFiles() = runTest(StandardTestDispatcher()) {
        val state = stateFor(FakeSource { flow { } })
        state.open(repo)
        advanceUntilIdle()
        assertEquals(LoadStatus.Done, state.uiState.value.status)
        assertEquals(emptyList(), state.uiState.value.files)
    }
}

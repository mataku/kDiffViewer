package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import io.github.mataku.kdiffviewer.core.model.Side
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitContentSourceTest {
  private class FakeWorkingTreeReader(
    var entry: UntrackedEntry = UntrackedEntry.Unreadable,
    var failure: Throwable? = null,
  ) : WorkingTreeReader {
    val calls = mutableListOf<Triple<String, String, Long>>()

    override suspend fun read(root: String, relativePath: String, maxBytes: Long): UntrackedEntry {
      calls += Triple(root, relativePath, maxBytes)
      failure?.let { throw it }
      return entry
    }
  }

  private val toplevel = listOf("rev-parse", "--show-toplevel")
  private val verifyHead = listOf("rev-parse", "--verify", "HEAD")

  private val runner = FakeGitCommandRunner().apply {
    runResults[toplevel] = GitResult(0, "/root\n", "")
    runResults[verifyHead] = GitResult(0, "abc123\n", "")
  }
  private val blobs = FakeGitBlobReader()
  private val tree = FakeWorkingTreeReader()
  private var factoryRoot: String? = null
  private val source = GitContentSource(runner, { root ->
    factoryRoot = root
    blobs
  }, tree)

  private fun file(oldPath: String?, newPath: String?) = FileDiff(oldPath, newPath, ChangeType.Modified, isUntracked = false, DiffContent.Text(emptyList()))

  private suspend fun session(base: ResolvedBase = ResolvedBase.Commit("HEAD", "abc123", "abc123")) = source.open(RepoPath("/repo"), base)

  @Test
  fun oldSideReadsBlobAtBaseCommit() = runTest {
    blobs.entry = BlobEntry.Regular("old\n".encodeToByteArray())
    assertEquals("old\n", session(ResolvedBase.Commit("origin/develop", "base1", "base1")).read(file("a.kt", "a.kt"), Side.Old, 1024))
    assertEquals(listOf(FakeGitBlobReader.Call("base1", "a.kt", 1024)), blobs.calls)
    assertTrue(runner.runCalls.none { it.args == verifyHead })
  }

  @Test
  fun blobReaderGetsRepositoryRoot() = runTest {
    session()
    assertEquals("/root", factoryRoot)
  }

  @Test
  fun emptyTreeBaseHasNoOldSide() = runTest {
    assertNull(session(ResolvedBase.EmptyTree).read(file("a.kt", "a.kt"), Side.Old, 1024))
    assertTrue(blobs.calls.isEmpty())
  }

  @Test
  fun oldSideIsNullForAddedFile() = runTest {
    assertNull(session().read(file(null, "a.kt"), Side.Old, 1024))
    assertTrue(blobs.calls.isEmpty())
  }

  @Test
  fun renamedFileReadsOldPathAndNewPath() = runTest {
    blobs.entry = BlobEntry.Regular("o".encodeToByteArray())
    tree.entry = UntrackedEntry.Regular("n".encodeToByteArray())
    val s = session()
    val renamed = file("a.txt", "b.kt")
    assertEquals("o", s.read(renamed, Side.Old, 1024))
    assertEquals("n", s.read(renamed, Side.New, 1024))
    assertEquals("a.txt", blobs.calls.single().path)
    assertEquals(Triple("/root", "b.kt", 1024L), tree.calls.single())
  }

  @Test
  fun newSideReadsWorkingTree() = runTest {
    tree.entry = UntrackedEntry.Regular("x\n".encodeToByteArray())
    assertEquals("x\n", session().read(file("a.kt", "a.kt"), Side.New, 1024))
  }

  @Test
  fun newSideIsNullForDeletedFile() = runTest {
    assertNull(session().read(file("a.kt", null), Side.New, 1024))
    assertTrue(tree.calls.isEmpty())
  }

  @Test
  fun unusableEntriesAreNull() = runTest {
    val s = session()
    val f = file("a.kt", "a.kt")
    for (entry in listOf(BlobEntry.TooLarge, BlobEntry.Missing)) {
      blobs.entry = entry
      assertNull(s.read(f, Side.Old, 1024))
    }
    for (entry in listOf(UntrackedEntry.TooLarge, UntrackedEntry.Symlink("t"), UntrackedEntry.Unreadable)) {
      tree.entry = entry
      assertNull(s.read(f, Side.New, 1024))
    }
  }

  @Test
  fun invalidUtf8IsReplaced() = runTest {
    blobs.entry = BlobEntry.Regular(byteArrayOf(0x61, 0xFF.toByte()))
    assertEquals("a�", session().read(file("a.kt", "a.kt"), Side.Old, 1024))
  }

  @Test
  fun blobReaderFailureIsNull() = runTest {
    blobs.failure = IllegalStateException("boom")
    assertNull(session().read(file("a.kt", "a.kt"), Side.Old, 1024))
  }

  @Test
  fun blobReaderCancellationPropagates() = runTest {
    blobs.failure = CancellationException("cancelled")
    assertFailsWith<CancellationException> { session().read(file("a.kt", "a.kt"), Side.Old, 1024) }
  }

  @Test
  fun workingTreeReaderFailureIsNull() = runTest {
    tree.failure = IllegalArgumentException("bad path")
    assertNull(session().read(file("a.kt", "a.kt"), Side.New, 1024))
  }

  @Test
  fun workingTreeReaderCancellationPropagates() = runTest {
    tree.failure = CancellationException("cancelled")
    assertFailsWith<CancellationException> { session().read(file("a.kt", "a.kt"), Side.New, 1024) }
  }

  @Test
  fun openOutsideRepositoryThrows() = runTest {
    runner.runResults[toplevel] = GitResult(128, "", "fatal")
    assertFailsWith<DiffLoadException.NotARepository> { session() }
  }

  @Test
  fun closeClosesBlobReader() = runTest {
    session().close()
    assertTrue(blobs.closed)
  }
}

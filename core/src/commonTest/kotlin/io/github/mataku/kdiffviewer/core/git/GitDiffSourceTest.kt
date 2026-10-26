package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitDiffSourceTest {
  private val runner = FakeGitCommandRunner()
  private val readPaths = mutableListOf<String>()
  private val reader = object : WorkingTreeReader {
    override suspend fun read(root: String, relativePath: String, maxBytes: Long): UntrackedEntry {
      readPaths += "$root|$relativePath|$maxBytes"
      return UntrackedEntry.Regular("x\n".encodeToByteArray())
    }
  }
  private val source = GitDiffSource(runner, reader)

  private val toplevel = listOf("rev-parse", "--show-toplevel")
  private val verifyHead = listOf("rev-parse", "--verify", "HEAD")
  private val lsFiles = listOf("ls-files", "--others", "--exclude-standard", "-z")

  private fun diffArgs(base: String) = listOf(
    "-c", "core.quotePath=false", "diff", base,
    "--no-color", "--no-ext-diff", "--no-textconv", "--src-prefix=a/", "--dst-prefix=b/", "-M", "--submodule=short",
  )

  private val modifiedDiff = listOf(
    "diff --git a/m.txt b/m.txt",
    "index 1111111..2222222 100644",
    "--- a/m.txt",
    "+++ b/m.txt",
    "@@ -1 +1 @@",
    "-old",
    "+new",
  )

  private fun repoSetup(untracked: String = "", diff: List<String> = emptyList()) {
    runner.runResults[toplevel] = GitResult(0, "/repo\n", "")
    runner.runResults[verifyHead] = GitResult(0, "abc\n", "")
    runner.runResults[lsFiles] = GitResult(0, untracked, "")
    runner.streams[diffArgs("HEAD")] = FakeGitCommandRunner.Stream(diff)
  }

  private suspend fun load(path: String = "/repo/sub"): List<FileDiff> = source.load(RepoPath(path)).toList()

  @Test
  fun loadsTrackedThenUntrackedAgainstHead() = runTest {
    repoSetup(untracked = "u.txt\u0000", diff = modifiedDiff)
    val result = load()
    assertEquals(2, result.size)
    assertEquals("m.txt", result[0].newPath)
    assertEquals(ChangeType.Modified, result[0].change)
    assertEquals("u.txt", result[1].newPath)
    assertTrue(result[1].isUntracked)
    assertEquals(listOf("/repo|u.txt|1048576"), readPaths)
    assertEquals("/repo/sub", runner.runCalls.first().workingDir)
    assertEquals(diffArgs("HEAD"), runner.streamCalls.single().args)
    assertEquals("/repo", runner.streamCalls.single().workingDir)
    val ls = runner.runCalls.single { it.args == lsFiles }
    assertEquals("/repo", ls.workingDir)
  }

  @Test
  fun usesEmptyTreeWhenHeadIsMissing() = runTest {
    repoSetup()
    runner.runResults[verifyHead] = GitResult(128, "", "fatal")
    runner.streams[diffArgs("4b825dc642cb6eb9a060e54bf8d69288fbee4904")] = FakeGitCommandRunner.Stream(modifiedDiff)
    val result = load()
    assertEquals(1, result.size)
    assertTrue("4b825dc642cb6eb9a060e54bf8d69288fbee4904" in runner.streamCalls.single().args)
    assertTrue("HEAD" !in runner.streamCalls.single().args)
  }

  @Test
  fun notARepository() = runTest {
    runner.runResults[toplevel] = GitResult(128, "", "fatal: not a git repository")
    val e = assertFailsWith<DiffLoadException.NotARepository> { load("/not/repo") }
    assertEquals("/not/repo", e.path)
  }

  @Test
  fun toplevelKeepsSurroundingSpacesAndOnlyDropsTrailingNewline() = runTest {
    runner.runResults[toplevel] = GitResult(0, " /repo \n", "")
    runner.runResults[listOf("rev-parse", "--verify", "HEAD")] = GitResult(0, "abc\n", "")
    runner.runResults[lsFiles] = GitResult(0, "", "")
    runner.streams[diffArgs("HEAD")] = FakeGitCommandRunner.Stream(emptyList())
    load()
    assertEquals(" /repo ", runner.streamCalls.single().workingDir)
  }

  @Test
  fun emptyToplevelOutputIsNotARepository() = runTest {
    runner.runResults[toplevel] = GitResult(0, "\n", "")
    val e = assertFailsWith<DiffLoadException.NotARepository> { load("/bare") }
    assertEquals("/bare", e.path)
  }

  @Test
  fun gitDiffFailure() = runTest {
    repoSetup()
    runner.streams[diffArgs("HEAD")] = FakeGitCommandRunner.Stream(emptyList(), 129, "boom")
    val e = assertFailsWith<DiffLoadException.GitFailed> { load() }
    assertEquals(129, e.exitCode)
    assertEquals("boom", e.stderr)
    assertEquals(diffArgs("HEAD"), e.args)
  }

  @Test
  fun lsFilesFailure() = runTest {
    repoSetup()
    runner.runResults[lsFiles] = GitResult(128, "", "ls boom")
    val e = assertFailsWith<DiffLoadException.GitFailed> { load() }
    assertEquals(128, e.exitCode)
    assertEquals("ls boom", e.stderr)
    assertEquals(lsFiles, e.args)
  }

  @Test
  fun directoryEntriesFromLsFilesAreSkipped() = runTest {
    repoSetup(untracked = "sub/\u0000a.txt\u0000")
    val result = load()
    assertEquals(listOf("a.txt"), result.map { it.newPath })
    assertEquals(listOf("/repo|a.txt|1048576"), readPaths)
  }

  @Test
  fun diffDisablesTextconv() = runTest {
    repoSetup()
    load()
    assertTrue("--no-textconv" in runner.streamCalls.single().args)
  }

  @Test
  fun noChangesEmitsNothing() = runTest {
    repoSetup()
    assertEquals(emptyList(), load())
  }

  private val symbolicRef = listOf("symbolic-ref", "--quiet", "--short", "HEAD")
  private val shortHead = listOf("rev-parse", "--short", "HEAD")

  @Test
  fun headIsBranchName() = runTest {
    runner.runResults[symbolicRef] = GitResult(0, "feature/x\n", "")
    assertEquals(HeadRef.Branch("feature/x"), source.head(RepoPath("/repo/sub")))
    assertEquals("/repo/sub", runner.runCalls.single().workingDir)
  }

  @Test
  fun detachedHeadIsShortHash() = runTest {
    runner.runResults[symbolicRef] = GitResult(1, "", "")
    runner.runResults[shortHead] = GitResult(0, "abc1234\n", "")
    assertEquals(HeadRef.Detached("abc1234"), source.head(RepoPath("/repo")))
  }

  @Test
  fun headIsNullWhenGitFails() = runTest {
    runner.runResults[symbolicRef] = GitResult(128, "", "fatal")
    runner.runResults[shortHead] = GitResult(128, "", "fatal")
    assertEquals(null, source.head(RepoPath("/repo")))
  }

  private val showToplevel = listOf("rev-parse", "--show-toplevel")

  @Test
  fun rootIsTheToplevelOfTheOpenedDirectory() = runTest {
    runner.runResults[showToplevel] = GitResult(0, "/repo\n", "")
    assertEquals(RepoPath("/repo"), source.root(RepoPath("/repo/sub")))
    assertEquals("/repo/sub", runner.runCalls.single().workingDir)
  }

  @Test
  fun rootIsNullOutsideARepository() = runTest {
    runner.runResults[showToplevel] = GitResult(128, "", "fatal: not a git repository")
    assertEquals(null, source.root(RepoPath("/tmp")))
  }
}

package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
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
  private val lsFiles = listOf("-c", "core.fsmonitor=false", "ls-files", "--others", "--exclude-standard", "-z")

  private fun diffArgs(base: String) = listOf(
    "-c", "core.quotePath=false", "-c", "core.fsmonitor=false", "-c", "diff.orderFile=/dev/null", "diff", base,
    "--no-color", "--no-ext-diff", "--no-textconv", "--src-prefix=a/", "--dst-prefix=b/", "-M", "--submodule=short", "--unified=3", "--inter-hunk-context=0",
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

  private val headBase = ResolvedBase.Commit("HEAD", "HEAD", "HEAD")

  private suspend fun load(path: String = "/repo/sub", base: ResolvedBase = headBase): List<FileDiff> = source.load(RepoPath(path), base).toList()

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

  private fun modified(path: String) = listOf(
    "diff --git a/$path b/$path",
    "index 1111111..2222222 100644",
    "--- a/$path",
    "+++ b/$path",
    "@@ -1 +1 @@",
    "-old",
    "+new",
  )

  @Test
  fun loadEmitsTrackedAndUntrackedFilesInTreeOrder() = runTest {
    repoSetup(untracked = "app/u.txt\u0000z.txt\u0000", diff = modified("README.md") + modified("app/x.kt") + modified("m.txt"))
    val result = load()
    assertEquals(listOf("app/u.txt", "app/x.kt", "README.md", "m.txt", "z.txt"), result.map { it.newPath })
    assertEquals(listOf(true, false, false, false, true), result.map { it.isUntracked })
  }

  @Test
  fun lsFilesRunsBeforeTheDiff() = runTest {
    repoSetup()
    runner.runResults[lsFiles] = GitResult(128, "", "ls boom")
    assertFailsWith<DiffLoadException.GitFailed> { load() }
    assertTrue(runner.streamCalls.isEmpty())
  }

  @Test
  fun loadDiffsAgainstGivenCommit() = runTest {
    repoSetup()
    runner.streams[diffArgs("m1")] = FakeGitCommandRunner.Stream(modifiedDiff)
    val result = load(base = ResolvedBase.Commit("origin/develop", "m1", "m1"))
    assertEquals(1, result.size)
    assertEquals(diffArgs("m1"), runner.streamCalls.single().args)
    assertTrue(runner.runCalls.none { it.args == verifyHead })
  }

  @Test
  fun emptyTreeBaseUsesEmptyTreeId() = runTest {
    repoSetup()
    runner.streams[diffArgs("4b825dc642cb6eb9a060e54bf8d69288fbee4904")] = FakeGitCommandRunner.Stream(modifiedDiff)
    val result = load(base = ResolvedBase.EmptyTree)
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

  private val originHead = listOf("symbolic-ref", "--quiet", "refs/remotes/origin/HEAD")
  private fun mergeBase(of: String) = listOf("merge-base", "--end-of-options", of, "h1")
  private fun verifyRef(ref: String) = listOf("rev-parse", "--verify", "--quiet", "--end-of-options", "$ref^{commit}")
  private fun short(id: String) = listOf("rev-parse", "--short", id)

  private fun baseSetup() {
    runner.runResults[toplevel] = GitResult(0, "/repo\n", "")
    runner.runResults[verifyHead] = GitResult(0, "h1\n", "")
    runner.runResults[short("h1")] = GitResult(0, "h1short\n", "")
  }

  private suspend fun resolve(spec: BaseSpec): ResolvedBase = source.resolveBase(RepoPath("/repo/sub"), spec)

  @Test
  fun defaultBaseIsMergeBaseWithOriginHead() = runTest {
    baseSetup()
    runner.runResults[originHead] = GitResult(0, "refs/remotes/origin/develop\n", "")
    runner.runResults[mergeBase("refs/remotes/origin/develop")] = GitResult(0, "m1\n", "")
    runner.runResults[short("m1")] = GitResult(0, "m1abc\n", "")
    assertEquals(ResolvedBase.Commit("origin/develop", "m1", "m1abc"), resolve(BaseSpec.Default))
    assertTrue(runner.runCalls.all { it.workingDir == "/repo/sub" })
  }

  @Test
  fun defaultBaseFallsBackToHeadWithoutOriginHead() = runTest {
    baseSetup()
    runner.runResults[originHead] = GitResult(1, "", "")
    assertEquals(ResolvedBase.Commit("HEAD", "h1", "h1short"), resolve(BaseSpec.Default))
  }

  @Test
  fun defaultBaseFallsBackToHeadWhenMergeBaseFails() = runTest {
    baseSetup()
    runner.runResults[originHead] = GitResult(0, "refs/remotes/origin/gone\n", "")
    runner.runResults[mergeBase("refs/remotes/origin/gone")] = GitResult(128, "", "fatal: Not a valid object name")
    assertEquals(ResolvedBase.Commit("HEAD", "h1", "h1short"), resolve(BaseSpec.Default))
  }

  @Test
  fun defaultBaseFallsBackToHeadWithoutCommonAncestor() = runTest {
    baseSetup()
    runner.runResults[originHead] = GitResult(0, "refs/remotes/origin/main\n", "")
    runner.runResults[mergeBase("refs/remotes/origin/main")] = GitResult(1, "", "")
    assertEquals(ResolvedBase.Commit("HEAD", "h1", "h1short"), resolve(BaseSpec.Default))
  }

  @Test
  fun explicitRefOutsideARepositoryIsNotARepository() = runTest {
    runner.runResults[toplevel] = GitResult(128, "", "fatal: not a git repository")
    runner.runResults[verifyHead] = GitResult(128, "", "fatal: not a git repository")
    runner.runResults[verifyRef("develop")] = GitResult(128, "", "fatal: not a git repository")
    assertEquals("/repo/sub", assertFailsWith<DiffLoadException.NotARepository> { resolve(BaseSpec.Ref("develop")) }.path)
  }

  @Test
  fun defaultBaseIsEmptyTreeWithoutHead() = runTest {
    runner.runResults[toplevel] = GitResult(0, "/repo\n", "")
    runner.runResults[verifyHead] = GitResult(128, "", "fatal")
    assertEquals(ResolvedBase.EmptyTree, resolve(BaseSpec.Default))
  }

  @Test
  fun explicitRefIsMergeBaseWithHead() = runTest {
    baseSetup()
    runner.runResults[verifyRef("HEAD~3")] = GitResult(0, "r1\n", "")
    runner.runResults[mergeBase("r1")] = GitResult(0, "r1\n", "")
    runner.runResults[short("r1")] = GitResult(0, "r1abc\n", "")
    assertEquals(ResolvedBase.Commit("HEAD~3", "r1", "r1abc"), resolve(BaseSpec.Ref("HEAD~3")))
  }

  @Test
  fun shortIdFallsBackToSevenCharacters() = runTest {
    baseSetup()
    runner.runResults[verifyRef("x")] = GitResult(0, "0123456789abcdef\n", "")
    runner.runResults[mergeBase("0123456789abcdef")] = GitResult(0, "0123456789abcdef\n", "")
    runner.runResults[short("0123456789abcdef")] = GitResult(128, "", "fatal")
    assertEquals(ResolvedBase.Commit("x", "0123456789abcdef", "0123456"), resolve(BaseSpec.Ref("x")))
  }

  @Test
  fun unknownRefThrowsUnknownBase() = runTest {
    baseSetup()
    runner.runResults[verifyRef("nope")] = GitResult(1, "", "")
    assertEquals("nope", assertFailsWith<DiffLoadException.UnknownBase> { resolve(BaseSpec.Ref("nope")) }.ref)
  }

  @Test
  fun unrelatedRefThrowsNoCommonAncestor() = runTest {
    baseSetup()
    runner.runResults[verifyRef("other")] = GitResult(0, "o1\n", "")
    runner.runResults[mergeBase("o1")] = GitResult(1, "", "")
    assertEquals("other", assertFailsWith<DiffLoadException.NoCommonAncestor> { resolve(BaseSpec.Ref("other")) }.ref)
  }

  @Test
  fun explicitRefWithoutHeadHasNoCommonAncestor() = runTest {
    runner.runResults[toplevel] = GitResult(0, "/repo\n", "")
    runner.runResults[verifyHead] = GitResult(128, "", "fatal")
    runner.runResults[verifyRef("x")] = GitResult(0, "x1\n", "")
    assertEquals("x", assertFailsWith<DiffLoadException.NoCommonAncestor> { resolve(BaseSpec.Ref("x")) }.ref)
  }

  @Test
  fun unknownRefWithoutHeadIsUnknownBase() = runTest {
    runner.runResults[toplevel] = GitResult(0, "/repo\n", "")
    runner.runResults[verifyHead] = GitResult(128, "", "fatal")
    runner.runResults[verifyRef("x")] = GitResult(1, "", "")
    assertEquals("x", assertFailsWith<DiffLoadException.UnknownBase> { resolve(BaseSpec.Ref("x")) }.ref)
  }

  @Test
  fun mergeBaseErrorOnExplicitRefIsGitFailed() = runTest {
    baseSetup()
    runner.runResults[verifyRef("x")] = GitResult(0, "x1\n", "")
    runner.runResults[mergeBase("x1")] = GitResult(128, "", "boom")
    val e = assertFailsWith<DiffLoadException.GitFailed> { resolve(BaseSpec.Ref("x")) }
    assertEquals(mergeBase("x1"), e.args)
    assertEquals("boom", e.stderr)
  }

  private val forEachRef = listOf("for-each-ref", "--format=%(refname)%00%(symref)", "--sort=-committerdate", "refs/heads", "refs/remotes")

  @Test
  fun branchesMapsLocalAndRemoteRefsInGitOrderAndSkipsSymrefs() = runTest {
    runner.runResults[forEachRef] = GitResult(
      0,
      "refs/heads/feature-x\u0000\nrefs/remotes/origin/HEAD\u0000refs/remotes/origin/develop\nrefs/remotes/origin/develop\u0000\nrefs/heads/main\u0000\n",
      "",
    )
    val result = source.branches(RepoPath("/repo/sub"))
    assertEquals(listOf(BranchRef("feature-x", false), BranchRef("origin/develop", true), BranchRef("main", false)), result)
    val call = runner.runCalls.single()
    assertEquals(forEachRef, call.args)
    assertEquals("/repo/sub", call.workingDir)
  }

  @Test
  fun branchesMapsAnyRemote() = runTest {
    runner.runResults[forEachRef] = GitResult(0, "refs/remotes/upstream/main\u0000\n", "")
    assertEquals(listOf(BranchRef("upstream/main", true)), source.branches(RepoPath("/repo")))
  }

  @Test
  fun branchesIsEmptyWithoutRefs() = runTest {
    runner.runResults[forEachRef] = GitResult(0, "", "")
    assertEquals(emptyList(), source.branches(RepoPath("/repo")))
  }

  @Test
  fun branchesFailureIsGitFailed() = runTest {
    runner.runResults[forEachRef] = GitResult(128, "", "fatal: x")
    val e = assertFailsWith<DiffLoadException.GitFailed> { source.branches(RepoPath("/repo")) }
    assertEquals(forEachRef, e.args)
    assertEquals(128, e.exitCode)
  }
}

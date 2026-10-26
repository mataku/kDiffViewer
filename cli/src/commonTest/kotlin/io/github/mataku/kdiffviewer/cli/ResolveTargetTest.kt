package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.RegistryEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveTargetTest {
  private fun entry(pid: Long, root: String?) = RegistryEntry(
    pid = pid,
    repoRoot = root,
    socket = "/s/$pid.sock",
    startedAt = "2026-10-26T00:00:00Z",
    protocolVersion = 1,
  )

  private val identity: (String) -> String? = { it }

  @Test
  fun subdirectoryResolvesToRepoEntry() {
    val r = entry(1, "/r")
    assertEquals(Resolution.Found(r), resolveTarget("/r/src/x", TargetOptions(), listOf(r), identity))
    assertEquals(Resolution.Found(r), resolveTarget("/r", TargetOptions(), listOf(r), identity))
  }

  @Test
  fun siblingWithSharedPrefixDoesNotMatch() {
    assertEquals(Resolution.NoWindow, resolveTarget("/r2", TargetOptions(), listOf(entry(1, "/r")), identity))
  }

  @Test
  fun longestRepoRootWins() {
    val outer = entry(1, "/r")
    val inner = entry(2, "/r/sub")
    assertEquals(Resolution.Found(inner), resolveTarget("/r/sub/a", TargetOptions(), listOf(outer, inner), identity))
    assertEquals(Resolution.Found(outer), resolveTarget("/r/other", TargetOptions(), listOf(outer, inner), identity))
  }

  @Test
  fun sameRepoInTwoWindowsIsAmbiguous() {
    val result = resolveTarget("/r", TargetOptions(), listOf(entry(1, "/r"), entry(2, "/r")), identity)
    assertEquals(Resolution.Ambiguous(listOf(1L, 2L)), result)
  }

  @Test
  fun pidBypassesPath() {
    val a = entry(1, "/a")
    val b = entry(2, "/b")
    assertEquals(Resolution.Found(b), resolveTarget("/a", TargetOptions(pid = 2), listOf(a, b), identity))
    assertEquals(Resolution.NoWindow, resolveTarget("/a", TargetOptions(pid = 3), listOf(a, b), identity))
  }

  @Test
  fun repoOptionIsResolvedThroughRealpath() {
    val r = entry(1, "/real/r")
    val realpath: (String) -> String? = { if (it == "/link/r") "/real/r" else null }
    assertEquals(Resolution.Found(r), resolveTarget("/cwd", TargetOptions(repo = "/link/r"), listOf(r), realpath))
    assertEquals(Resolution.NoWindow, resolveTarget("/cwd", TargetOptions(repo = "/missing"), listOf(r), realpath))
  }

  @Test
  fun symlinkedRepoOptionMatchesRealRoot() {
    val r = entry(1, "/real/r")
    val realpath: (String) -> String? = { if (it == "/link/r/sub") "/real/r/sub" else null }
    assertEquals(Resolution.Found(r), resolveTarget("/cwd", TargetOptions(repo = "/link/r/sub"), listOf(r), realpath))
  }

  @Test
  fun trailingSlashInRepoRootStillMatches() {
    val r = entry(1, "/r/")
    assertEquals(Resolution.Found(r), resolveTarget("/r/src", TargetOptions(), listOf(r), identity))
    assertEquals(Resolution.NoWindow, resolveTarget("/r2", TargetOptions(), listOf(r), identity))
  }

  @Test
  fun entriesWithoutRepoRootNeverMatch() {
    assertEquals(Resolution.NoWindow, resolveTarget("/r", TargetOptions(), listOf(entry(1, null)), identity))
  }
}

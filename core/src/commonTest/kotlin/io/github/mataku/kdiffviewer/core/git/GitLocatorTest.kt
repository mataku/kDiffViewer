package io.github.mataku.kdiffviewer.core.git

import kotlin.test.Test
import kotlin.test.assertEquals

class GitLocatorTest {
  private class FakeProbe(private val accepted: Set<String>) : (String) -> GitInfo? {
    val calls = mutableListOf<String>()

    override fun invoke(path: String): GitInfo? {
      calls += path
      return if (path in accepted) GitInfo(path, "git version 2") else null
    }
  }

  @Test
  fun configuredPathIsTheOnlyCandidate() {
    val probe = FakeProbe(setOf("/c/git", "/a/git"))

    val result = GitLocator.locate("/c/git", "/a", probe)

    assertEquals(GitLocation.Found(GitInfo("/c/git", "git version 2")), result)
    assertEquals(listOf("/c/git"), probe.calls)
  }

  @Test
  fun rejectedConfiguredDoesNotFallBack() {
    val probe = FakeProbe(setOf("/a/git", "/usr/bin/git"))

    val result = GitLocator.locate("/c/git", "/a", probe)

    assertEquals(GitLocation.ConfiguredUnavailable("/c/git"), result)
    assertEquals(listOf("/c/git"), probe.calls)
  }

  @Test
  fun relativePathEntriesAreNotProbed() {
    val probe = FakeProbe(emptySet())
    GitLocator.locate(null, ".:bin:/a", probe)
    assertEquals(listOf("/a/git") + GitLocator.FALLBACK_PATHS, probe.calls)
  }

  @Test
  fun usrBinOnPathIsTriedLast() {
    val probe = FakeProbe(emptySet())

    GitLocator.locate(null, "/usr/bin:/a", probe)

    assertEquals(listOf("/a/git", "/opt/homebrew/bin/git", "/usr/local/bin/git", "/usr/bin/git"), probe.calls)
  }

  @Test
  fun usrBinWithTrailingSlashIsAlsoSkipped() {
    val probe = FakeProbe(emptySet())

    GitLocator.locate(null, "/usr/bin/:/a", probe)

    assertEquals(listOf("/a/git", "/opt/homebrew/bin/git", "/usr/local/bin/git", "/usr/bin/git"), probe.calls)
  }

  @Test
  fun duplicatesAreProbedOnce() {
    val probe = FakeProbe(emptySet())

    GitLocator.locate(null, "/opt/homebrew/bin:/a:/a", probe)

    assertEquals(probe.calls.distinct(), probe.calls)
    assertEquals(listOf("/opt/homebrew/bin/git", "/a/git", "/usr/local/bin/git", "/usr/bin/git"), probe.calls)
  }

  @Test
  fun emptySegmentsAreIgnored() {
    val probe = FakeProbe(emptySet())

    GitLocator.locate(null, ":/a::", probe)

    assertEquals(listOf("/a/git") + GitLocator.FALLBACK_PATHS, probe.calls)
  }

  @Test
  fun notFoundWhenNothingAccepted() {
    assertEquals(GitLocation.NotFound, GitLocator.locate(null, "/a", FakeProbe(emptySet())))
  }
}

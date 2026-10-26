package io.github.mataku.kdiffviewer.core.git

import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitLocatorHostTest {
  @Test
  fun probeAcceptsScriptPrintingGitVersion() {
    val script = writeScript(newTempDir(), "git", "echo 'git version 9.9.9'")

    assertEquals(GitInfo(script.path, "git version 9.9.9"), probeGit(script.path))
  }

  @Test
  fun probeRejectsNonZeroExit() {
    val script = writeScript(newTempDir(), "git", "echo 'git version 1'; exit 1")

    assertNull(probeGit(script.path))
  }

  @Test
  fun probeRejectsUnexpectedOutput() {
    val script = writeScript(newTempDir(), "git", "echo 'xcrun: error: invalid active developer path'")

    assertNull(probeGit(script.path))
  }

  @Test
  fun probeTimesOut() {
    val script = writeScript(newTempDir(), "git", "sleep 5")

    var result: GitInfo? = GitInfo("", "")
    val elapsed = measureTimeMillis { result = probeGit(script.path, timeoutMillis = 200) }

    assertNull(result)
    assertTrue(elapsed < 2_000, "probe took $elapsed ms")
  }

  @Test
  fun probeRejectsDirectory() {
    val dir = newTempDir()
    File(dir, "git").mkdirs()

    assertNull(probeGit(File(dir, "git").path))
  }

  @Test
  fun locateOnHostSkipsDirectoryNamedGit() {
    val withDirectory = newTempDir()
    File(withDirectory, "git").mkdirs()
    val withScript = newTempDir()
    val script = writeScript(withScript, "git", "echo 'git version 9.9.9'")

    val located = GitLocator.locateOnHost(null, "${withDirectory.path}:${withScript.path}")

    val found = located as GitLocation.Found
    assertEquals(script.path, found.info.path)
  }
}

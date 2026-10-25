package io.github.mataku.kdiffviewer.core.git

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class GitLocatorHostTest {
  @Test
  fun skipsDirectoryNamedGitOnPath() {
    val withDirectory = newTempDir()
    File(withDirectory, "git").mkdirs()
    val withScript = newTempDir()
    val script = writeScript(withScript, "git", "exit 0")

    val located = GitLocator.locateOnHost("${withDirectory.path}:${withScript.path}")

    assertEquals(script.path, located)
  }
}

package io.github.mataku.kdiffviewer.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistryTest {
  @Test
  fun sessionsDirIsUnderApplicationSupport() {
    assertEquals(
      "/Users/a/Library/Application Support/kDiffViewer/sessions",
      RegistryPaths.sessionsDir("/Users/a", HostOs.MacOs),
    )
  }

  @Test
  fun linuxSessionsDirIsUnderLocalState() {
    assertEquals(
      "/home/a/.local/state/kDiffViewer/sessions",
      RegistryPaths.sessionsDir("/home/a", HostOs.Linux),
    )
  }

  @Test
  fun socketAndEntryPathsUsePid() {
    val dir = RegistryPaths.sessionsDir("/Users/a", HostOs.MacOs)
    assertTrue(RegistryPaths.socketPath(dir, 42).endsWith("/42.sock"))
    assertTrue(RegistryPaths.entryPath(dir, 42).endsWith("/42.json"))
  }

  @Test
  fun fitsSocketPathBoundary() {
    assertTrue(RegistryPaths.fitsSocketPath("a".repeat(103)))
    assertFalse(RegistryPaths.fitsSocketPath("a".repeat(104)))
  }

  @Test
  fun fitsSocketPathCountsUtf8Bytes() {
    assertTrue(RegistryPaths.fitsSocketPath("a".repeat(101) + "é"))
    assertFalse(RegistryPaths.fitsSocketPath("a".repeat(102) + "é"))
    assertFalse(RegistryPaths.fitsSocketPath("あ".repeat(35)))
  }
}

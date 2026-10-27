package io.github.mataku.kdiffviewer.core.git

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FileSystemWorkingTreeReaderTest {
  private val root = newTempDir()
  private val reader = FileSystemWorkingTreeReader()

  @Test
  fun readsRegularFile() = runBlocking<Unit> {
    File(root, "a.txt").writeBytes(byteArrayOf(1, 2, 3))
    val entry = reader.read(root.path, "a.txt", 100)
    assertContentEquals(byteArrayOf(1, 2, 3), assertIs<UntrackedEntry.Regular>(entry).bytes)
  }

  @Test
  fun fileExactlyAtLimitIsRead() = runBlocking<Unit> {
    File(root, "a.txt").writeBytes(ByteArray(10))
    assertIs<UntrackedEntry.Regular>(reader.read(root.path, "a.txt", 10))
  }

  @Test
  fun overLimitIsTooLarge() = runBlocking<Unit> {
    File(root, "a.txt").writeBytes(ByteArray(11))
    assertEquals(UntrackedEntry.TooLarge, reader.read(root.path, "a.txt", 10))
  }

  @Test
  fun symlinkReturnsTarget() = runBlocking<Unit> {
    Files.createSymbolicLink(File(root, "link").toPath(), File("target.txt").toPath())
    assertEquals(UntrackedEntry.Symlink("target.txt"), reader.read(root.path, "link", 100))
  }

  @Test
  fun directoryIsUnreadable() = runBlocking<Unit> {
    File(root, "d").mkdir()
    assertEquals(UntrackedEntry.Unreadable, reader.read(root.path, "d", 100))
  }

  @Test
  fun missingFileIsUnreadable() = runBlocking<Unit> {
    assertEquals(UntrackedEntry.Unreadable, reader.read(root.path, "nope", 100))
  }

  @Test
  fun parentTraversalIsUnreadable() = runBlocking<Unit> {
    File(root.parentFile, "outside-${root.name}.txt").apply {
      writeText("secret")
      deleteOnExit()
    }
    assertEquals(UntrackedEntry.Unreadable, reader.read(root.path, "../outside-${root.name}.txt", 100))
  }

  @Test
  fun absolutePathIsUnreadable() = runBlocking<Unit> {
    assertEquals(UntrackedEntry.Unreadable, reader.read(root.path, "/etc/hosts", 1_000_000))
  }

  @Test
  fun rootItselfIsUnreadable() = runBlocking<Unit> {
    assertEquals(UntrackedEntry.Unreadable, reader.read(root.path, ".", 100))
  }

  @Test
  fun dotDotInsideRootIsRead() = runBlocking<Unit> {
    File(root, "d").mkdir()
    File(root, "a.txt").writeBytes(byteArrayOf(7))
    assertIs<UntrackedEntry.Regular>(reader.read(root.path, "d/../a.txt", 100))
  }
}

package io.github.mataku.kdiffviewer.app.settings

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FileSettingsStorageTest {
  private val root = Files.createTempDirectory("kdv-settings-test")

  @Test
  fun readReturnsNullWhenMissing() {
    assertNull(FileSettingsStorage(root.resolve("settings.json")).read())
  }

  @Test
  fun writeCreatesMissingDirectories() {
    val storage = FileSettingsStorage(root.resolve("a").resolve("b").resolve("settings.json"))
    storage.write("{\"fontSize\": 14}")
    assertEquals("{\"fontSize\": 14}", storage.read())
  }

  @Test
  fun writeReplacesExistingContent() {
    val storage = FileSettingsStorage(root.resolve("settings.json"))
    storage.write("1")
    storage.write("2")
    assertEquals("2", storage.read())
    assertEquals(emptyList(), root.listDirectoryEntries("*.tmp"))
  }

  @Test
  fun backupMovesFileAside() {
    val file = root.resolve("settings.json")
    val storage = FileSettingsStorage(file)
    storage.write("x")
    storage.backup()
    assertNull(storage.read())
    assertEquals("x", root.resolve("settings.json.bak").readText())
  }

  @Test
  fun backupReplacesExistingBackup() {
    val storage = FileSettingsStorage(root.resolve("settings.json"))
    storage.write("old")
    storage.backup()
    storage.write("new")
    storage.backup()
    assertEquals("new", root.resolve("settings.json.bak").readText())
  }

  @Test
  fun backupWithoutFileDoesNothing() {
    FileSettingsStorage(root.resolve("settings.json")).backup()
    assertFalse(root.resolve("settings.json.bak").exists())
  }
}

package io.github.mataku.kdiffviewer.app.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class FileSettingsStorage(private val file: Path) : SettingsStorage {
  override fun read(): String? = if (file.exists()) file.readText() else null

  override fun write(text: String) {
    val dir = file.toAbsolutePath().parent
    dir.createDirectories()
    val tmp = Files.createTempFile(dir, "settings", ".tmp")
    try {
      tmp.writeText(text)
      Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
      Files.deleteIfExists(tmp)
    }
  }

  override fun backup() {
    if (!file.exists()) return
    Files.move(file, file.resolveSibling("settings.json.bak"), REPLACE_EXISTING)
  }
}

fun defaultSettingsFile(): Path = Path(System.getProperty("user.home"), "Library", "Application Support", "kDiffViewer", "settings.json")

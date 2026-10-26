package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.PROTOCOL_VERSION
import io.github.mataku.kdiffviewer.protocol.RegistryEntry
import io.github.mataku.kdiffviewer.protocol.RegistryPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant

fun processAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

class SessionRegistry(
  private val dir: Path,
  private val pid: Long,
  now: () -> Instant,
  private val isAlive: (Long) -> Boolean,
) {
  private val socketPath: Path = Path.of(RegistryPaths.socketPath(dir.toString(), pid))
  private val entryPath: Path = Path.of(RegistryPaths.entryPath(dir.toString(), pid))
  private val startedAt: String = now().toString()

  @Volatile
  var repoRoot: String? = null
    private set

  fun prepare(): Path? {
    if (!RegistryPaths.fitsSocketPath(socketPath.toString())) return null
    Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
    sweepDeadSessions()
    return socketPath
  }

  fun publish(repoRoot: Path?) {
    val root = repoRoot?.toRealPath()?.toString()
    val entry = RegistryEntry(
      pid = pid,
      repoRoot = root,
      socket = socketPath.toString(),
      startedAt = startedAt,
      protocolVersion = PROTOCOL_VERSION,
    )
    val temp = Files.createTempFile(dir, "$pid-", ".tmp")
    try {
      Files.writeString(temp, Codec.json.encodeToString(RegistryEntry.serializer(), entry))
      Files.move(temp, entryPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      this.repoRoot = root
    } finally {
      Files.deleteIfExists(temp)
    }
  }

  fun remove() {
    Files.deleteIfExists(entryPath)
    Files.deleteIfExists(socketPath)
  }

  private fun sweepDeadSessions() {
    Files.newDirectoryStream(dir).use { entries ->
      for (path in entries) {
        val name = path.fileName.toString()
        val pidText = when {
          name.endsWith(".json") -> name.removeSuffix(".json")
          name.endsWith(".sock") -> name.removeSuffix(".sock")
          else -> continue
        }
        val other = pidText.toLongOrNull() ?: continue
        if (other != pid && !isAlive(other)) Files.deleteIfExists(path)
      }
    }
  }
}

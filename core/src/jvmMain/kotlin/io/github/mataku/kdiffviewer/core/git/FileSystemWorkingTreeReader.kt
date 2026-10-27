package io.github.mataku.kdiffviewer.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Paths

class FileSystemWorkingTreeReader : WorkingTreeReader {
  override suspend fun read(root: String, relativePath: String, maxBytes: Long): UntrackedEntry = withContext(Dispatchers.IO) {
    try {
      val base = Paths.get(root).toAbsolutePath().normalize()
      val path = base.resolve(relativePath).normalize()
      when {
        path == base || !path.startsWith(base) -> UntrackedEntry.Unreadable
        Files.isSymbolicLink(path) -> UntrackedEntry.Symlink(Files.readSymbolicLink(path).toString())
        !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> UntrackedEntry.Unreadable
        Files.size(path) > maxBytes -> UntrackedEntry.TooLarge
        else -> UntrackedEntry.Regular(Files.readAllBytes(path))
      }
    } catch (e: IOException) {
      UntrackedEntry.Unreadable
    } catch (e: InvalidPathException) {
      UntrackedEntry.Unreadable
    }
  }
}

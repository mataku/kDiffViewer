package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.Side
import kotlin.coroutines.cancellation.CancellationException

interface ContentSource {
  suspend fun open(repo: RepoPath): ContentSession
}

interface ContentSession : AutoCloseable {
  suspend fun read(file: FileDiff, side: Side, maxBytes: Long): String?
}

class GitContentSource(
  private val runner: GitCommandRunner,
  private val blobReaderFactory: (root: String) -> GitBlobReader,
  private val reader: WorkingTreeReader,
) : ContentSource {
  override suspend fun open(repo: RepoPath): ContentSession {
    val rootResult = runner.run(repo.value, listOf("rev-parse", "--show-toplevel"))
    val root = rootResult.stdout.trimEnd('\n')
    if (rootResult.exitCode != 0 || root.isEmpty()) throw DiffLoadException.NotARepository(repo.value)

    val head = runner.run(root, listOf("rev-parse", "--verify", "HEAD"))
    val headId = if (head.exitCode == 0) head.stdout.trim().ifEmpty { null } else null
    return GitContentSession(root, headId, blobReaderFactory(root), reader)
  }
}

private class GitContentSession(
  private val root: String,
  private val headId: String?,
  private val blobReader: GitBlobReader,
  private val reader: WorkingTreeReader,
) : ContentSession {
  override suspend fun read(file: FileDiff, side: Side, maxBytes: Long): String? = when (side) {
    Side.Old -> readOld(file.oldPath, maxBytes)
    Side.New -> readNew(file.newPath, maxBytes)
  }

  private suspend fun readOld(path: String?, maxBytes: Long): String? {
    if (path == null || headId == null) return null
    return readOrNull {
      (blobReader.read(headId, path, maxBytes) as? BlobEntry.Regular)?.bytes?.decodeToString()
    }
  }

  private suspend fun readNew(path: String?, maxBytes: Long): String? {
    if (path == null) return null
    return readOrNull {
      (reader.read(root, path, maxBytes) as? UntrackedEntry.Regular)?.bytes?.decodeToString()
    }
  }

  private inline fun <T> readOrNull(block: () -> T?): T? = try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    null
  }

  override fun close() = blobReader.close()
}

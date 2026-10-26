package io.github.mataku.kdiffviewer.protocol

object RegistryPaths {
  private const val MAX_SOCKET_PATH_BYTES = 103

  fun sessionsDir(home: String): String = "$home/Library/Application Support/kDiffViewer/sessions"

  fun socketPath(dir: String, pid: Long): String = "$dir/$pid.sock"

  fun entryPath(dir: String, pid: Long): String = "$dir/$pid.json"

  fun fitsSocketPath(path: String): Boolean = path.encodeToByteArray().size <= MAX_SOCKET_PATH_BYTES
}

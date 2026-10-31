package io.github.mataku.kdiffviewer.protocol

enum class HostOs { MacOs, Linux }

object RegistryPaths {
  private const val MAX_SOCKET_PATH_BYTES = 103

  // Linux does not follow $XDG_STATE_HOME or $XDG_RUNTIME_DIR: the app and kdv can run with different environments
  // (a desktop launch and an ssh shell), and both must resolve the same directory.
  fun sessionsDir(home: String, os: HostOs): String = when (os) {
    HostOs.MacOs -> "$home/Library/Application Support/kDiffViewer/sessions"
    HostOs.Linux -> "$home/.local/state/kDiffViewer/sessions"
  }

  fun socketPath(dir: String, pid: Long): String = "$dir/$pid.sock"

  fun entryPath(dir: String, pid: Long): String = "$dir/$pid.json"

  fun fitsSocketPath(path: String): Boolean = path.encodeToByteArray().size <= MAX_SOCKET_PATH_BYTES
}

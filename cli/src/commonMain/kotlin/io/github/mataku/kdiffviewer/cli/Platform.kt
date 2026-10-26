package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.RegistryEntry

interface CliPlatform {
  fun cwd(): String

  fun home(): String

  fun env(name: String): String?

  fun realpath(path: String): String?

  fun listRegistry(dir: String): List<RegistryEntry>

  fun isAlive(pid: Long): Boolean

  fun request(socket: String, line: String): String

  fun readStdin(): String

  fun launchApp(app: String, path: String): Boolean
}

class TransportException(message: String) : Exception(message)

package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.CommentDto
import io.github.mataku.kdiffviewer.protocol.HostOs
import io.github.mataku.kdiffviewer.protocol.Methods
import io.github.mataku.kdiffviewer.protocol.RegistryEntry
import io.github.mataku.kdiffviewer.protocol.RegistryPaths
import io.github.mataku.kdiffviewer.protocol.ReloadResult
import io.github.mataku.kdiffviewer.protocol.ReviewWaitParams
import io.github.mataku.kdiffviewer.protocol.ReviewWaitResult
import io.github.mataku.kdiffviewer.protocol.ThreadDto
import io.github.mataku.kdiffviewer.protocol.ThreadListParams
import io.github.mataku.kdiffviewer.protocol.ThreadListResult
import io.github.mataku.kdiffviewer.protocol.ThreadReplyParams
import io.github.mataku.kdiffviewer.protocol.ThreadResolveParams
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val DEFAULT_APP = "/Applications/kDiffViewer.app"

private class CliFailure(message: String) : Exception(message)

fun run(args: List<String>, platform: CliPlatform, out: (String) -> Unit, err: (String) -> Unit): Int = when (val parsed = parseArgs(args)) {
  is ParseResult.UsageError -> {
    err("kdv: ${parsed.message}")
    err(usage(parsed.topic).trimEnd('\n'))
    2
  }

  is ParseResult.Ok -> try {
    execute(parsed.command, platform, out)
  } catch (e: CliFailure) {
    err("kdv: ${e.message}")
    1
  }
}

private fun execute(command: Command, platform: CliPlatform, out: (String) -> Unit): Int {
  when (command) {
    is Command.Help -> out(usage(command.topic).trimEnd('\n'))

    is Command.AgentGuide -> out(agentGuide().trimEnd('\n'))

    is Command.Open -> open(command, platform)

    is Command.Windows -> {
      val entries = liveEntries(platform)
      if (entries.isEmpty()) out("No windows.")
      entries.forEach { out("${it.pid}  ${it.repoRoot ?: "(no repository)"}") }
    }

    is Command.ThreadList -> {
      val params = encode(ThreadListParams.serializer(), ThreadListParams(command.all))
      val response = call(platform, command.target, Methods.THREAD_LIST, params)
      if (command.json) {
        out(Codec.json.encodeToString(JsonElement.serializer(), response))
      } else {
        out(formatThreads(decode(ThreadListResult.serializer(), response).threads, command.all))
      }
    }

    is Command.ThreadReply -> {
      val body = if (command.body == "-") platform.readStdin().trimEnd('\n') else command.body
      val params = encode(ThreadReplyParams.serializer(), ThreadReplyParams(command.id, body, command.author))
      decode(CommentDto.serializer(), call(platform, command.target, Methods.THREAD_REPLY, params))
      out("Replied to ${command.id}.")
    }

    is Command.ThreadResolve -> {
      val params = encode(ThreadResolveParams.serializer(), ThreadResolveParams(command.id))
      decode(ThreadDto.serializer(), call(platform, command.target, Methods.THREAD_RESOLVE, params))
      out("Resolved ${command.id}.")
    }

    is Command.ReviewWait -> return reviewWait(command, platform, out)

    is Command.Reload -> {
      val result = decode(ReloadResult.serializer(), call(platform, command.target, Methods.RELOAD, JsonObject(emptyMap())))
      out("Reloaded: ${result.files} files, ${result.outdated} outdated threads.")
    }
  }
  return 0
}

private const val TIMED_OUT_EXIT = 3

private fun reviewWait(command: Command.ReviewWait, platform: CliPlatform, out: (String) -> Unit): Int {
  val params = encode(ReviewWaitParams.serializer(), ReviewWaitParams(command.timeoutSeconds))
  val response = call(platform, command.target, Methods.REVIEW_WAIT, params, closedMessage = "the kDiffViewer window closed")
  val result = decode(ReviewWaitResult.serializer(), response)
  when {
    command.json -> out(Codec.json.encodeToString(JsonElement.serializer(), response))
    result.timedOut -> out("Timed out.")
    else -> out(formatReviews(result))
  }
  return if (result.timedOut) TIMED_OUT_EXIT else 0
}

private fun open(command: Command.Open, platform: CliPlatform) {
  val requested = command.path ?: platform.cwd()
  val path = platform.realpath(requested) ?: throw CliFailure("no such directory: $requested")
  val app = platform.env("KDV_APP") ?: DEFAULT_APP
  if (!platform.launchApp(app, path, command.base)) throw CliFailure("kDiffViewer not found at $app (set KDV_APP)")
}

private fun liveEntries(platform: CliPlatform): List<RegistryEntry> = platform.listRegistry(RegistryPaths.sessionsDir(platform.home(), HostOs.MacOs)).filter { platform.isAlive(it.pid) }.sortedBy { it.pid }

private fun resolve(platform: CliPlatform, target: TargetOptions): RegistryEntry {
  val cwd = platform.cwd()
  val cwdReal = platform.realpath(cwd) ?: cwd
  val base = target.repo?.let { platform.realpath(it) } ?: target.repo ?: cwdReal
  return when (val resolution = resolveTarget(cwdReal, target, liveEntries(platform), platform::realpath)) {
    is Resolution.Found -> resolution.entry
    is Resolution.NoWindow -> throw CliFailure("no kDiffViewer window is open for $base\nOpen one with: kdv ./")
    is Resolution.Ambiguous -> throw CliFailure("several windows show $base; pass --pid (${resolution.pids.joinToString(", ")})")
  }
}

private fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject = Codec.json.encodeToJsonElement(serializer, value) as JsonObject

private fun call(platform: CliPlatform, target: TargetOptions, method: String, params: JsonObject, closedMessage: String? = null): JsonElement {
  val entry = resolve(platform, target)
  val line = Codec.encodeRequest(method, params)
  val raw = try {
    platform.request(entry.socket, line)
  } catch (e: TransportException) {
    throw CliFailure(closedMessage ?: "lost connection to kDiffViewer (${e.message})")
  }
  val response = try {
    Codec.decodeResponse(raw)
  } catch (e: Exception) {
    throw CliFailure("invalid response from kDiffViewer (${e.message?.lineSequence()?.firstOrNull()})")
  }
  response.error?.let { throw CliFailure(it.message) }
  if (!response.ok) throw CliFailure("request failed")
  return response.result ?: throw CliFailure("invalid response from kDiffViewer (missing result)")
}

private fun <T> decode(serializer: KSerializer<T>, element: JsonElement): T = try {
  Codec.json.decodeFromJsonElement(serializer, element)
} catch (e: Exception) {
  throw CliFailure("invalid response from kDiffViewer (${e.message?.lineSequence()?.firstOrNull()})")
}

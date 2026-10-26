package io.github.mataku.kdiffviewer.cli

private class ParsedOptions(
  val positionals: List<String>,
  val all: Boolean,
  val json: Boolean,
  val author: String,
  val target: TargetOptions,
)

private sealed interface OptionsResult {
  data class Ok(val options: ParsedOptions) : OptionsResult

  data class Error(val message: String) : OptionsResult
}

private fun parseOptions(args: List<String>): OptionsResult {
  val positionals = mutableListOf<String>()
  var all = false
  var json = false
  var author = "agent"
  var repo: String? = null
  var pid: Long? = null
  var i = 0
  while (i < args.size) {
    val arg = args[i]
    when {
      arg == "--" -> {
        positionals += args.drop(i + 1)
        break
      }

      arg == "--all" -> all = true

      arg == "--json" -> json = true

      arg == "--author" || arg == "--repo" || arg == "--pid" -> {
        val value = args.getOrNull(i + 1) ?: return OptionsResult.Error("missing value for $arg")
        i++
        when (arg) {
          "--author" -> author = value
          "--repo" -> repo = value
          else -> pid = value.toLongOrNull() ?: return OptionsResult.Error("invalid --pid '$value'")
        }
      }

      arg.length > 1 && arg.startsWith("-") -> return OptionsResult.Error("unknown option '$arg'")

      else -> positionals += arg
    }
    i++
  }
  return OptionsResult.Ok(ParsedOptions(positionals, all, json, author, TargetOptions(repo, pid)))
}

private val VALUE_OPTIONS = setOf("--author", "--repo", "--pid")

private fun helpRequested(args: List<String>): Boolean {
  var i = 0
  while (i < args.size) {
    val arg = args[i]
    when {
      arg == "--" -> return false
      arg == "-h" || arg == "--help" -> return true
      arg in VALUE_OPTIONS -> i++
    }
    i++
  }
  return false
}

fun parseArgs(args: List<String>): ParseResult {
  if (args.isEmpty()) return ParseResult.Ok(Command.Open(null))
  val first = args.first()
  val isPath = first == "." || first == ".." || first.contains('/')
  val topic = if (first == "thread") "thread" else null
  if (helpRequested(args)) return ParseResult.Ok(Command.Help(topic))
  if (isPath) return ParseResult.Ok(Command.Open(first))

  val rest = args.drop(1)
  val options = when (val parsed = parseOptions(rest)) {
    is OptionsResult.Ok -> parsed.options
    is OptionsResult.Error -> return ParseResult.UsageError(parsed.message, topic)
  }
  val target = options.target
  val positionals = options.positionals

  fun noPositionals(command: Command): ParseResult = if (positionals.isEmpty()) {
    ParseResult.Ok(command)
  } else {
    ParseResult.UsageError("unexpected argument '${positionals.first()}'", null)
  }

  return when (first) {
    "thread" -> parseThread(positionals, options)
    "reload" -> noPositionals(Command.Reload(target))
    "windows" -> noPositionals(Command.Windows(target))
    "agent-guide" -> noPositionals(Command.AgentGuide(target))
    else -> ParseResult.UsageError("unknown command '$first'\nTo open a directory, pass a path: kdv ./$first", null)
  }
}

private fun parseThread(positionals: List<String>, options: ParsedOptions): ParseResult {
  val target = options.target
  fun error(message: String) = ParseResult.UsageError(message, "thread")
  return when (val sub = positionals.firstOrNull()) {
    "list" -> if (positionals.size > 1) {
      error("unexpected argument '${positionals[1]}'")
    } else {
      ParseResult.Ok(Command.ThreadList(options.all, options.json, target))
    }

    "reply" -> {
      val id = positionals.getOrNull(1) ?: return error("thread reply needs <id> and <body|->")
      val body = positionals.getOrNull(2) ?: return error("thread reply needs <body|->")
      if (positionals.size > 3) {
        return error("thread reply takes a single <body>: quote it, or pass - to read it from stdin")
      }
      ParseResult.Ok(Command.ThreadReply(id, body, options.author, target))
    }

    "resolve" -> {
      val id = positionals.getOrNull(1) ?: return error("thread resolve needs <id>")
      if (positionals.size > 2) return error("unexpected argument '${positionals[2]}'")
      ParseResult.Ok(Command.ThreadResolve(id, target))
    }

    null -> error("thread needs a subcommand: list, reply, resolve")

    else -> error("unknown thread subcommand '$sub'")
  }
}

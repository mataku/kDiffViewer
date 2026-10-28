package io.github.mataku.kdiffviewer.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseArgsTest {
  private fun ok(vararg args: String): Command {
    val result = parseArgs(args.toList())
    assertTrue(result is ParseResult.Ok, "expected Ok but was $result")
    return result.command
  }

  private fun error(vararg args: String): ParseResult.UsageError {
    val result = parseArgs(args.toList())
    assertTrue(result is ParseResult.UsageError, "expected UsageError but was $result")
    return result
  }

  @Test
  fun noArgumentOpensCurrentDirectory() {
    assertEquals(Command.Open(null), ok())
  }

  @Test
  fun openWithBase() {
    assertEquals(Command.Open("./a", "develop"), ok("./a", "--base", "develop"))
    assertEquals(Command.Open(null, "HEAD~3"), ok("--base", "HEAD~3"))
    assertEquals(Command.Open("./a", "x"), ok("--base", "x", "./a"))
  }

  @Test
  fun openRejectsBadArguments() {
    assertEquals("missing value for --base", error("./a", "--base").message)
    assertEquals("invalid --base '-x'", error("./a", "--base", "-x").message)
    assertEquals("unknown option '--foo'", error("./a", "--foo").message)
    assertEquals("unexpected argument './b'", error("./a", "./b").message)
  }

  @Test
  fun pathLikeFirstArgumentOpens() {
    assertEquals(Command.Open("."), ok("."))
    assertEquals(Command.Open("./a"), ok("./a"))
    assertEquals(Command.Open("/abs"), ok("/abs"))
    assertEquals(Command.Open("src/x"), ok("src/x"))
    assertEquals(Command.Open(".."), ok(".."))
  }

  @Test
  fun unknownWordIsUsageError() {
    val result = error("kDiffViewer")
    assertEquals("unknown command 'kDiffViewer'\nTo open a directory, pass a path: kdv ./kDiffViewer", result.message)
    assertEquals(null, result.topic)
  }

  @Test
  fun helpFlags() {
    assertEquals(Command.Help(null), ok("--help"))
    assertEquals(Command.Help(null), ok("-h"))
    assertEquals(Command.Help("thread"), ok("thread", "-h"))
    assertEquals(Command.Help("thread"), ok("thread", "reply", "--help"))
  }

  @Test
  fun threadReplyDefaultsAuthorToAgent() {
    assertEquals(
      Command.ThreadReply("t1", "-", "agent", TargetOptions()),
      ok("thread", "reply", "t1", "-"),
    )
  }

  @Test
  fun threadReplyHonorsAuthor() {
    assertEquals(
      Command.ThreadReply("t1", "done", "codex", TargetOptions()),
      ok("thread", "reply", "t1", "done", "--author", "codex"),
    )
  }

  @Test
  fun threadListFlags() {
    assertEquals(
      Command.ThreadList(all = true, json = true, target = TargetOptions()),
      ok("thread", "list", "--all", "--json"),
    )
    assertEquals(
      Command.ThreadList(all = false, json = false, target = TargetOptions()),
      ok("thread", "list"),
    )
  }

  @Test
  fun threadResolve() {
    assertEquals(Command.ThreadResolve("t1", TargetOptions()), ok("thread", "resolve", "t1"))
  }

  @Test
  fun simpleCommandsCarryTargetOptions() {
    val target = TargetOptions(repo = "/r", pid = 42)
    assertEquals(Command.Reload(target), ok("reload", "--repo", "/r", "--pid", "42"))
    assertEquals(Command.Windows(TargetOptions()), ok("windows"))
    assertEquals(Command.AgentGuide(TargetOptions()), ok("agent-guide"))
  }

  @Test
  fun missingArgumentsAreThreadUsageErrors() {
    assertEquals("thread", error("thread", "reply", "t1").topic)
    assertEquals("thread", error("thread", "reply").topic)
    assertEquals("thread", error("thread", "resolve").topic)
    assertEquals("thread", error("thread").topic)
    assertEquals("thread", error("thread", "bogus").topic)
  }

  @Test
  fun nonNumericPidIsUsageError() {
    assertEquals("thread", error("thread", "list", "--pid", "x").topic)
    error("reload", "--pid", "x")
  }

  @Test
  fun extraPositionalsAreRejected() {
    val reply = error("thread", "reply", "t1", "fixed", "the", "bug")
    assertEquals("thread", reply.topic)
    assertTrue(reply.message.contains("quote"))
    assertTrue(reply.message.contains("-"))
    assertEquals("thread", error("thread", "list", "foo").topic)
    assertEquals("thread", error("thread", "resolve", "t1", "x").topic)
    error("reload", "x")
  }

  @Test
  fun doubleDashMakesRemainingArgumentsPositional() {
    assertEquals(
      Command.ThreadReply("t1", "- item", "agent", TargetOptions()),
      ok("thread", "reply", "t1", "--", "- item"),
    )
    assertEquals(
      Command.ThreadReply("t1", "--help", "agent", TargetOptions()),
      ok("thread", "reply", "t1", "--", "--help"),
    )
  }

  @Test
  fun helpIsNotDetectedAsOptionValue() {
    assertEquals(
      Command.ThreadReply("t1", "x", "-h", TargetOptions()),
      ok("thread", "reply", "t1", "x", "--author", "-h"),
    )
  }

  @Test
  fun optionWithoutValueIsUsageError() {
    assertEquals("thread", error("thread", "list", "--author").topic)
    assertEquals("thread", error("thread", "list", "--repo").topic)
  }

  @Test
  fun unknownOptionIsUsageError() {
    assertEquals("thread", error("thread", "list", "--bogus").topic)
  }

  @Test
  fun messagesCarryNoPrefix() {
    assertEquals("unknown option '--bogus'", error("thread", "list", "--bogus").message)
    assertEquals("missing value for --repo", error("reload", "--repo").message)
  }

  @Test
  fun usageTextMatchesSpec() {
    val expected = """
      kdv [path] [--base <ref>]                Open path in kDiffViewer (no argument: current directory)
                                               Base defaults to the merge-base with origin/HEAD
      kdv thread list [--all] [--json]         Unresolved threads (all with --all)
      kdv thread reply <id> <body|-> [--author <name>]
      kdv thread resolve <id>
      kdv review wait [--timeout <s>] [--json] Wait until the user sends comments from the app
      kdv reload                               Reload the diff and re-anchor comments
      kdv windows                              Running windows (pid, repository)
      kdv agent-guide                          How a coding agent should use kdv
      Common options: --repo <path>, --pid <pid>, -h/--help
    """.trimIndent() + "\n"
    assertEquals(expected, usage(null))
  }

  @Test
  fun threadUsageListsOnlyThreadLines() {
    val text = usage("thread")
    assertTrue(text.contains("kdv thread list"))
    assertTrue(text.contains("kdv thread reply"))
    assertTrue(text.contains("kdv thread resolve"))
    assertTrue(!text.contains("kdv reload"))
  }

  @Test
  fun threadUsageExactText() {
    val expected = """
      kdv thread list [--all] [--json]         Unresolved threads (all with --all)
      kdv thread reply <id> <body|-> [--author <name>]
      kdv thread resolve <id>
      Common options: --repo <path>, --pid <pid>, -h/--help
    """.trimIndent() + "\n"
    assertEquals(expected, usage("thread"))
  }

  @Test
  fun reviewWait() {
    assertEquals(Command.ReviewWait(540, true, TargetOptions()), ok("review", "wait", "--timeout", "540", "--json"))
    assertEquals(Command.ReviewWait(null, false, TargetOptions(repo = "/r")), ok("review", "wait", "--repo", "/r"))
  }

  @Test
  fun reviewWaitRejectsBadTimeout() {
    assertEquals("invalid --timeout '0': use 1 to 86400 seconds", error("review", "wait", "--timeout", "0").message)
    assertEquals("invalid --timeout 'x': use 1 to 86400 seconds", error("review", "wait", "--timeout", "x").message)
    assertEquals("invalid --timeout '86401': use 1 to 86400 seconds", error("review", "wait", "--timeout", "86401").message)
    assertEquals("review", error("review", "wait", "--timeout", "x").topic)
  }

  @Test
  fun reviewNeedsSubcommand() {
    assertEquals("review needs a subcommand: wait", error("review").message)
    assertEquals("unknown review subcommand 'nope'", error("review", "nope").message)
    assertEquals("unexpected argument 'extra'", error("review", "wait", "extra").message)
  }

  @Test
  fun reviewHelpTopic() {
    assertEquals(Command.Help("review"), ok("review", "--help"))
    assertEquals(Command.Help("review"), ok("review", "wait", "--timeout", "5", "-h"))
  }

  @Test
  fun timeoutOnlyForReviewWait() {
    assertEquals("--timeout is only for kdv review wait", error("thread", "list", "--timeout", "5").message)
    assertEquals("--timeout is only for kdv review wait", error("reload", "--timeout", "5").message)
  }
}

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
  fun usageTextMatchesSpec() {
    val expected = """
      kdv [path]                               Open path in kDiffViewer (no argument: current directory)
      kdv thread list [--all] [--json]         Unresolved threads (all with --all)
      kdv thread reply <id> <body|-> [--author <name>]
      kdv thread resolve <id>
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
}

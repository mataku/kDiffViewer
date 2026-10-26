package io.github.mataku.kdiffviewer.cli

import io.github.mataku.kdiffviewer.protocol.AuthorDto
import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.CommentDto
import io.github.mataku.kdiffviewer.protocol.ErrorCodes
import io.github.mataku.kdiffviewer.protocol.RegistryEntry
import io.github.mataku.kdiffviewer.protocol.ReloadResult
import io.github.mataku.kdiffviewer.protocol.RequestEnvelope
import io.github.mataku.kdiffviewer.protocol.ThreadDto
import io.github.mataku.kdiffviewer.protocol.ThreadListResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakePlatform(
  val cwd: String = "/work/repo/sub",
  val entries: List<RegistryEntry> = listOf(entry(100, "/work/repo")),
  val alive: Set<Long> = setOf(100, 101),
  val stdin: String = "",
  val appExists: Boolean = true,
  val respond: (String, String) -> String = { _, _ -> error("unexpected request") },
) : CliPlatform {
  val requests = mutableListOf<Pair<String, RequestEnvelope>>()
  var launched: Triple<String, String, String?>? = null
  val envVars = mutableMapOf<String, String>()

  override fun cwd() = cwd

  override fun home() = "/home/u"

  override fun env(name: String) = envVars[name]

  override fun realpath(path: String): String? = when {
    path.startsWith("/") -> path
    path.contains("missing") -> null
    else -> "$cwd/${path.removePrefix("./")}"
  }

  override fun listRegistry(dir: String): List<RegistryEntry> = entries

  override fun isAlive(pid: Long) = pid in alive

  override fun request(socket: String, line: String): String {
    requests += socket to Codec.decodeRequest(line)
    return respond(socket, line)
  }

  override fun readStdin() = stdin

  override fun launchApp(app: String, path: String, base: String?): Boolean {
    launched = Triple(app, path, base)
    return appExists
  }
}

private fun entry(pid: Long, root: String?) = RegistryEntry(pid, root, "/s/$pid.sock", "2026-10-26T00:00:00Z", 1)

private class Output {
  val out = StringBuilder()
  val err = StringBuilder()
}

private fun exec(platform: CliPlatform, vararg args: String): Triple<Int, String, String> {
  val o = Output()
  val code = run(args.toList(), platform, { o.out.append(it).append('\n') }, { o.err.append(it).append('\n') })
  return Triple(code, o.out.toString(), o.err.toString())
}

private val sampleThread = ThreadDto(
  "t1",
  "A.kt",
  "new",
  3,
  3,
  listOf("x"),
  false,
  false,
  listOf(CommentDto("c1", AuthorDto("user"), "fix", "2026-10-26T00:00:00Z")),
)

private fun okThreads(threads: List<ThreadDto>) = Codec.success(Codec.json.encodeToJsonElement(ThreadListResult.serializer(), ThreadListResult(threads)))

class RunTest {
  @Test
  fun helpPrintsUsageToOut() {
    val (code, out, err) = exec(FakePlatform(), "--help")
    assertEquals(0, code)
    assertEquals(usage(null), out)
    assertEquals("", err)
  }

  @Test
  fun threadHelpPrintsThreadUsage() {
    val (code, out, _) = exec(FakePlatform(), "thread", "--help")
    assertEquals(0, code)
    assertEquals(usage("thread"), out)
  }

  @Test
  fun unknownCommandPrintsErrorAndUsageToErr() {
    val (code, out, err) = exec(FakePlatform(), "nope")
    assertEquals(2, code)
    assertEquals("", out)
    assertEquals("kdv: unknown command 'nope'\nTo open a directory, pass a path: kdv ./nope\n" + usage(null), err)
  }

  @Test
  fun threadUsageErrorUsesThreadTopic() {
    val (code, _, err) = exec(FakePlatform(), "thread")
    assertEquals(2, code)
    assertEquals("kdv: thread needs a subcommand: list, reply, resolve\n" + usage("thread"), err)
  }

  @Test
  fun threadListSendsAllFalseAndPrintsFormatted() {
    val p = FakePlatform(respond = { _, _ -> okThreads(listOf(sampleThread)) })
    val (code, out, _) = exec(p, "thread", "list")
    assertEquals(0, code)
    assertEquals(formatThreads(listOf(sampleThread), all = false) + "\n", out)
    val (socket, request) = p.requests.single()
    assertEquals("/s/100.sock", socket)
    assertEquals("thread.list", request.method)
    assertEquals(false, request.params["all"]?.jsonPrimitive?.boolean ?: false)
  }

  @Test
  fun threadListAllSendsAllTrue() {
    val p = FakePlatform(respond = { _, _ -> okThreads(emptyList()) })
    val (code, out, _) = exec(p, "thread", "list", "--all")
    assertEquals(0, code)
    assertEquals("No threads.\n", out)
    assertEquals(true, p.requests.single().second.params["all"]?.jsonPrimitive?.boolean)
  }

  @Test
  fun threadListJsonPrintsResultJson() {
    val p = FakePlatform(respond = { _, _ -> okThreads(listOf(sampleThread)) })
    val (code, out, _) = exec(p, "thread", "list", "--json")
    assertEquals(0, code)
    val decoded = Codec.json.decodeFromString(ThreadListResult.serializer(), out.trim())
    assertEquals(ThreadListResult(listOf(sampleThread)), decoded)
    assertEquals(1, out.trim().lines().size)
  }

  @Test
  fun threadReplyReadsBodyFromStdin() {
    val comment = CommentDto("c2", AuthorDto("agent", "agent"), "line1\nline2", "2026-10-26T00:00:00Z")
    val p = FakePlatform(stdin = "line1\nline2\n", respond = { _, _ -> Codec.success(Codec.json.encodeToJsonElement(CommentDto.serializer(), comment)) })
    val (code, out, _) = exec(p, "thread", "reply", "t1", "-")
    assertEquals(0, code)
    assertEquals("Replied to t1.\n", out)
    val params = p.requests.single().second.params
    assertEquals("thread.reply", p.requests.single().second.method)
    assertEquals("t1", params["threadId"]?.jsonPrimitive?.content)
    assertEquals("line1\nline2", params["body"]?.jsonPrimitive?.content)
    assertEquals("agent", params["author"]?.jsonPrimitive?.content)
  }

  @Test
  fun threadReplyWithLiteralBodyAndAuthor() {
    val comment = CommentDto("c2", AuthorDto("agent", "me"), "ok", "2026-10-26T00:00:00Z")
    val p = FakePlatform(respond = { _, _ -> Codec.success(Codec.json.encodeToJsonElement(CommentDto.serializer(), comment)) })
    exec(p, "thread", "reply", "t1", "ok", "--author", "me")
    val params = p.requests.single().second.params
    assertEquals("ok", params["body"]?.jsonPrimitive?.content)
    assertEquals("me", params["author"]?.jsonPrimitive?.content)
  }

  @Test
  fun threadResolveSendsThreadId() {
    val resolved = sampleThread.copy(resolved = true)
    val p = FakePlatform(respond = { _, _ -> Codec.success(Codec.json.encodeToJsonElement(ThreadDto.serializer(), resolved)) })
    val (code, out, _) = exec(p, "thread", "resolve", "t1")
    assertEquals(0, code)
    assertEquals("Resolved t1.\n", out)
    assertEquals("t1", p.requests.single().second.params["threadId"]?.jsonPrimitive?.content)
  }

  @Test
  fun reloadPrintsCounts() {
    val p = FakePlatform(respond = { _, _ -> Codec.success(Codec.json.encodeToJsonElement(ReloadResult.serializer(), ReloadResult(12, 2))) })
    val (code, out, _) = exec(p, "reload")
    assertEquals(0, code)
    assertEquals("Reloaded: 12 files, 2 outdated threads.\n", out)
  }

  @Test
  fun appErrorIsPrintedWithExit1() {
    val p = FakePlatform(respond = { _, _ -> Codec.failure(ErrorCodes.THREAD_NOT_FOUND, "No thread t9") })
    val (code, out, err) = exec(p, "thread", "resolve", "t9")
    assertEquals(1, code)
    assertEquals("", out)
    assertEquals("kdv: No thread t9\n", err)
  }

  @Test
  fun noWindowMessage() {
    val p = FakePlatform(entries = emptyList())
    val (code, _, err) = exec(p, "thread", "list")
    assertEquals(1, code)
    assertEquals("kdv: no kDiffViewer window is open for /work/repo/sub\nOpen one with: kdv ./\n", err)
  }

  @Test
  fun deadEntriesAreIgnored() {
    val p = FakePlatform(alive = emptySet())
    val (code, _, err) = exec(p, "reload")
    assertEquals(1, code)
    assertTrue(err.startsWith("kdv: no kDiffViewer window is open"))
  }

  @Test
  fun ambiguousMessage() {
    val p = FakePlatform(entries = listOf(entry(100, "/work/repo"), entry(101, "/work/repo")))
    val (code, _, err) = exec(p, "reload")
    assertEquals(1, code)
    assertEquals("kdv: several windows show /work/repo/sub; pass --pid (100, 101)\n", err)
  }

  @Test
  fun transportFailure() {
    val p = FakePlatform(respond = { _, _ -> throw TransportException("EOF") })
    val (code, out, err) = exec(p, "thread", "list")
    assertEquals(1, code)
    assertEquals("", out)
    assertEquals("kdv: lost connection to kDiffViewer (EOF)\n", err)
  }

  @Test
  fun malformedResponseIsAnError() {
    val p = FakePlatform(respond = { _, _ -> "not json" })
    val (code, _, err) = exec(p, "thread", "list")
    assertEquals(1, code)
    assertTrue(err.startsWith("kdv: invalid response from kDiffViewer ("), err)
  }

  @Test
  fun missingResultIsAnError() {
    val p = FakePlatform(respond = { _, _ -> """{"ok":true}""" })
    val (code, _, err) = exec(p, "thread", "list")
    assertEquals(1, code)
    assertTrue(err.startsWith("kdv: invalid response from kDiffViewer ("), err)
  }

  @Test
  fun windowsListsLiveEntries() {
    val p = FakePlatform(entries = listOf(entry(100, "/work/repo"), entry(101, null), entry(102, "/dead")))
    val (code, out, _) = exec(p, "windows")
    assertEquals(0, code)
    assertEquals("100  /work/repo\n101  (no repository)\n", out)
    assertTrue(p.requests.isEmpty())
  }

  @Test
  fun windowsWithoutLiveEntriesSaysSo() {
    val p = FakePlatform(entries = listOf(entry(102, "/dead")))
    val (code, out, _) = exec(p, "windows")
    assertEquals(0, code)
    assertEquals("No windows.\n", out)
  }

  @Test
  fun openUsesRealpathAndDefaultApp() {
    val p = FakePlatform()
    val (code, _, _) = exec(p, "/abs/dir")
    assertEquals(0, code)
    assertEquals(Triple("/Applications/kDiffViewer.app", "/abs/dir", null), p.launched)
  }

  @Test
  fun openWithoutArgumentUsesCwdAndKdvApp() {
    val p = FakePlatform()
    p.envVars["KDV_APP"] = "/opt/kdv.app"
    val (code, _, _) = exec(p)
    assertEquals(0, code)
    assertEquals(Triple("/opt/kdv.app", "/work/repo/sub", null), p.launched)
  }

  @Test
  fun openForwardsBase() {
    val p = FakePlatform()
    val (code, _, _) = exec(p, "--base", "develop")
    assertEquals(0, code)
    assertEquals(Triple("/Applications/kDiffViewer.app", "/work/repo/sub", "develop"), p.launched)
  }

  @Test
  fun openWithBadBaseIsUsageErrorAndLaunchesNothing() {
    val p = FakePlatform()
    val (code, _, err) = exec(p, "./", "--base", "-x")
    assertEquals(2, code)
    assertTrue(err.startsWith("kdv: invalid --base '-x'\n"), err)
    assertEquals(null, p.launched)
  }

  @Test
  fun openWithMissingAppPrintsNotFound() {
    val p = FakePlatform(appExists = false)
    val (code, _, err) = exec(p, "./x")
    assertEquals(1, code)
    assertEquals("kdv: kDiffViewer not found at /Applications/kDiffViewer.app (set KDV_APP)\n", err)
  }

  @Test
  fun openWithMissingPathIsAnError() {
    val p = FakePlatform()
    val (code, _, err) = exec(p, "./missing")
    assertEquals(1, code)
    assertEquals("kdv: no such directory: ./missing\n", err)
    assertEquals(null, p.launched)
  }

  @Test
  fun agentGuideMentionsWorkflow() {
    val (code, out, _) = exec(FakePlatform(), "agent-guide")
    assertEquals(0, code)
    for (needle in listOf("kdv thread list --json", "kdv thread reply <id> - <<'EOF'", "kdv thread resolve <id>", "kdv reload", "outdated")) {
      assertTrue(out.contains(needle), needle)
    }
  }
}

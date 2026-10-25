package io.github.mataku.kdiffviewer.core.git

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProcessGitCommandRunnerTest {
    private val dir = newTempDir()

    @Test
    fun streamsStdoutLines() = runBlocking<Unit> {
        val runner = ProcessGitCommandRunner(requireGit())
        val lines = runner.streamLines(dir.path, listOf("--version")).toList()
        assertTrue(lines.single().startsWith("git version"))
    }

    @Test
    fun runCapturesStdout() = runBlocking<Unit> {
        val result = ProcessGitCommandRunner(requireGit()).run(dir.path, listOf("--version"))
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.startsWith("git version"))
    }

    @Test
    fun nonZeroExitThrowsWithStderr() = runBlocking<Unit> {
        val runner = ProcessGitCommandRunner(requireGit())
        val e = assertFailsWith<GitCommandFailedException> {
            runner.streamLines(dir.path, listOf("no-such-command")).toList()
        }
        assertNotEquals(0, e.exitCode)
        assertTrue(e.stderr.isNotBlank())
    }

    @Test
    fun nonZeroExitIsThrownAfterAllStdoutLines() = runBlocking<Unit> {
        val script = writeScript(dir, "fail.sh", "printf 'one\\ntwo\\n'; echo boom >&2; exit 3")
        val runner = ProcessGitCommandRunner(script.path)
        val received = mutableListOf<String>()
        val e = assertFailsWith<GitCommandFailedException> {
            runner.streamLines(dir.path, emptyList()).collect { received += it }
        }
        assertEquals(listOf("one", "two"), received)
        assertEquals(3, e.exitCode)
        assertEquals("boom", e.stderr.trim())
    }

    @Test
    fun largeStderrDoesNotDeadlock() = runBlocking<Unit> {
        val script = writeScript(
            dir,
            "noisy.sh",
            "head -c 1048576 /dev/zero | tr '\\0' 'x' >&2\nfor i in 1 2 3 4 5 6 7 8 9 10; do echo line\$i; done",
        )
        val runner = ProcessGitCommandRunner(script.path)
        val lines = withTimeout(10_000) { runner.streamLines(dir.path, emptyList()).toList() }
        assertEquals(10, lines.size)
        val result = withTimeout(10_000) { runner.run(dir.path, emptyList()) }
        assertEquals(0, result.exitCode)
        assertEquals(1_048_576, result.stderr.length)
    }

    @Test
    fun loneCarriageReturnStaysInsideLine() = runBlocking<Unit> {
        val script = writeScript(dir, "cr.sh", "printf 'a\\rb\\nc\\r\\nlast'")
        val lines = ProcessGitCommandRunner(script.path).streamLines(dir.path, emptyList()).toList()
        assertEquals(listOf("a\rb", "c", "last"), lines)
    }

    @Test
    fun multibyteUtf8SurvivesChunkBoundaries() = runBlocking<Unit> {
        val script = writeScript(dir, "utf8.sh", "i=0; while [ \$i -lt 20000 ]; do printf 'あいう\\n'; i=\$((i+1)); done")
        val lines = ProcessGitCommandRunner(script.path).streamLines(dir.path, emptyList()).toList()
        assertEquals(20000, lines.size)
        assertTrue(lines.all { it == "あいう" })
    }

    @Test
    fun takeOnLongStreamCompletesPromptly() = runBlocking<Unit> {
        val script = writeScript(dir, "forever.sh", "while true; do echo line; done")
        val lines = withTimeout(10_000) {
            ProcessGitCommandRunner(script.path).streamLines(dir.path, emptyList()).take(1).toList()
        }
        assertEquals(listOf("line"), lines)
    }

    @Test
    fun missingWorkingDirReturnsFailure() = runBlocking<Unit> {
        val result = ProcessGitCommandRunner(requireGit()).run("/no/such/dir", listOf("status"))
        assertNotEquals(0, result.exitCode)
    }

    @Test
    fun missingWorkingDirThrowsFromStream() = runBlocking<Unit> {
        val e = assertFailsWith<GitCommandFailedException> {
            ProcessGitCommandRunner(requireGit()).streamLines("/no/such/dir", listOf("status")).toList()
        }
        assertEquals(-1, e.exitCode)
    }

    @Test
    fun cancellingSilentStreamDestroysProcessPromptly() = runBlocking<Unit> {
        val script = writeScript(dir, "silent.sh", "exec sleep 30")
        val started = System.nanoTime()
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(1000) { ProcessGitCommandRunner(script.path).streamLines(dir.path, emptyList()).toList() }
        }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5000)
    }

    @Test
    fun cancellingSilentRunDestroysProcessPromptly() = runBlocking<Unit> {
        val script = writeScript(dir, "silent.sh", "exec sleep 30")
        val started = System.nanoTime()
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(1000) { ProcessGitCommandRunner(script.path).run(dir.path, emptyList()) }
        }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5000)
    }
}

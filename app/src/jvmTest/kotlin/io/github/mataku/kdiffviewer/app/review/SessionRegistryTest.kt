package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.protocol.Codec
import io.github.mataku.kdiffviewer.protocol.PROTOCOL_VERSION
import io.github.mataku.kdiffviewer.protocol.RegistryEntry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRegistryTest {
  private val base: Path = Files.createTempDirectory(Path.of("/tmp"), "kdv")
  private val dir: Path = base.resolve("sessions")
  private val now = Instant.parse("2026-10-26T01:02:03Z")

  @AfterTest
  fun tearDown() {
    base.toFile().deleteRecursively()
  }

  private fun registry(
    pid: Long = 100,
    alive: Set<Long> = setOf(100),
    dir: Path = this.dir,
  ) = SessionRegistry(dir, pid, { now }, { it in alive })

  @Test
  fun `prepare creates a private directory and returns the socket path`() {
    val socket = registry().prepare()
    assertEquals(dir.resolve("100.sock"), socket)
    assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)))
  }

  @Test
  fun `prepare sweeps dead pids and keeps live ones`() {
    Files.createDirectories(dir)
    listOf("200.json", "200.sock", "300.json", "300.sock").forEach { Files.writeString(dir.resolve(it), "x") }
    registry(alive = setOf(100, 300)).prepare()
    assertFalse(Files.exists(dir.resolve("200.json")))
    assertFalse(Files.exists(dir.resolve("200.sock")))
    assertTrue(Files.exists(dir.resolve("300.json")))
    assertTrue(Files.exists(dir.resolve("300.sock")))
  }

  @Test
  fun `prepare returns null when the socket path is too long`() {
    val longDir = base.resolve("a".repeat(120))
    assertNull(registry(dir = longDir).prepare())
  }

  @Test
  fun `publish writes a decodable entry`() {
    val repo = Files.createDirectories(base.resolve("repo"))
    val registry = registry()
    registry.prepare()
    registry.publish(repo)
    val entry = Codec.json.decodeFromString(RegistryEntry.serializer(), Files.readString(dir.resolve("100.json")))
    assertEquals(
      RegistryEntry(100, repo.toRealPath().toString(), dir.resolve("100.sock").toString(), now.toString(), PROTOCOL_VERSION),
      entry,
    )
  }

  @Test
  fun `publish stores the real path of a symlink`() {
    val repo = Files.createDirectories(base.resolve("repo"))
    val link = Files.createSymbolicLink(base.resolve("link"), repo)
    val registry = registry()
    registry.prepare()
    registry.publish(link)
    val entry = Codec.json.decodeFromString(RegistryEntry.serializer(), Files.readString(dir.resolve("100.json")))
    assertEquals(repo.toRealPath().toString(), entry.repoRoot)
  }

  @Test
  fun `publish with no repository stores a null root and leaves no temp files`() {
    val registry = registry()
    registry.prepare()
    registry.publish(null)
    val entry = Codec.json.decodeFromString(RegistryEntry.serializer(), Files.readString(dir.resolve("100.json")))
    assertNull(entry.repoRoot)
    assertEquals(listOf("100.json"), Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() })
  }

  @Test
  fun `startedAt stays at construction time across publishes`() {
    var clock = now
    val registry = SessionRegistry(dir, 100, { clock }, { true })
    registry.prepare()
    clock = now.plusSeconds(60)
    registry.publish(null)
    val entry = Codec.json.decodeFromString(RegistryEntry.serializer(), Files.readString(dir.resolve("100.json")))
    assertEquals(now.toString(), entry.startedAt)
  }

  @Test
  fun `remove deletes both files`() {
    val registry = registry()
    registry.prepare()
    registry.publish(null)
    Files.writeString(dir.resolve("100.sock"), "x")
    registry.remove()
    assertFalse(Files.exists(dir.resolve("100.json")))
    assertFalse(Files.exists(dir.resolve("100.sock")))
  }

  @Test
  fun `processAlive reports the current process`() {
    assertTrue(processAlive(ProcessHandle.current().pid()))
  }
}

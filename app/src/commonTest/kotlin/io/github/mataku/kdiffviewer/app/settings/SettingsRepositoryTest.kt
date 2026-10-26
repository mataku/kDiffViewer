package io.github.mataku.kdiffviewer.app.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
  private fun TestScope.repository(
    storage: FakeSettingsStorage,
    failures: MutableList<Throwable> = mutableListOf(),
  ) = SettingsRepository(
    storage = storage,
    scope = CoroutineScope(Job()),
    ioDispatcher = StandardTestDispatcher(testScheduler),
    onWriteFailure = { failures += it },
  )

  @Test
  fun missingFileIsOk() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage(content = null)
    val repository = repository(storage)
    advanceUntilIdle()

    assertEquals(Settings(), repository.settings.value)
    assertEquals(SettingsLoad.Ok, repository.load)
    assertTrue(storage.writes.isEmpty())
  }

  @Test
  fun unreadableFileIsNeverWritten() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage(content = "{")
    val repository = repository(storage)

    assertEquals(Settings(), repository.settings.value)
    assertEquals(SettingsLoad.Unreadable, repository.load)

    repository.update { it.copy(fontSize = 14) }
    repository.update { it.copy(fontSize = 16) }
    advanceUntilIdle()

    assertEquals(16, repository.settings.value.fontSize)
    assertTrue(storage.writes.isEmpty())
    assertEquals(0, storage.backupCalls)
    assertTrue(storage.events.isEmpty())
  }

  @Test
  fun throwingReadIsUnreadable() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage(failOnRead = true)
    val repository = repository(storage)

    assertEquals(Settings(), repository.settings.value)
    assertEquals(SettingsLoad.Unreadable, repository.load)

    repository.update { it.copy(fontSize = 14) }
    advanceUntilIdle()

    assertEquals(14, repository.settings.value.fontSize)
    assertTrue(storage.events.isEmpty())
  }

  @Test
  fun partiallyResetKeepsValidFieldsAndBacksUpThenWrites() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage(content = """{"fontSize":"big","tabWidth":4}""")
    val repository = repository(storage)

    assertEquals(Settings(tabWidth = 4), repository.settings.value)
    assertEquals(SettingsLoad.PartiallyReset, repository.load)
    assertTrue(storage.events.isEmpty())

    repository.update { it.copy(fontSize = 14) }
    advanceUntilIdle()
    repository.update { it.copy(fontSize = 16) }
    advanceUntilIdle()

    assertEquals(1, storage.backupCalls)
    assertEquals(listOf("backup", "write", "write"), storage.events)
  }

  @Test
  fun validFileIsOk() = runTest(StandardTestDispatcher()) {
    val repository = repository(FakeSettingsStorage(content = """{"fontSize":14}"""))

    assertEquals(SettingsLoad.Ok, repository.load)
    assertEquals(14, repository.settings.value.fontSize)
  }

  @Test
  fun updateAppliesImmediatelyAndWritesInBackground() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage()
    val repository = repository(storage)

    repository.update { it.copy(fontSize = 14) }

    assertEquals(14, repository.settings.value.fontSize)
    assertTrue(storage.writes.isEmpty())
    advanceUntilIdle()
    assertEquals(1, storage.writes.size)
    assertEquals(14, decodeSettings(storage.writes.single())?.fontSize)
  }

  @Test
  fun burstOfUpdatesWritesOnlyLatest() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage()
    val repository = repository(storage)

    repository.update { it.copy(fontSize = 10) }
    repository.update { it.copy(fontSize = 11) }
    repository.update { it.copy(fontSize = 13) }
    advanceUntilIdle()

    assertEquals(1, storage.writes.size)
    assertEquals(13, decodeSettings(storage.writes.single())?.fontSize)
  }

  @Test
  fun updateIsNormalized() = runTest(StandardTestDispatcher()) {
    val repository = repository(FakeSettingsStorage())

    repository.update { it.copy(fontSize = 100) }

    assertEquals(32, repository.settings.value.fontSize)
  }

  @Test
  fun noOpUpdateDoesNotWrite() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage()
    val repository = repository(storage)

    repository.update { it }
    advanceUntilIdle()

    assertTrue(storage.writes.isEmpty())
  }

  @Test
  fun writeFailureKeepsInMemoryValue() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage(failOnWrite = true)
    val failures = mutableListOf<Throwable>()
    val repository = repository(storage, failures)

    repository.update { it.copy(fontSize = 14) }
    advanceUntilIdle()

    assertEquals(14, repository.settings.value.fontSize)
    assertEquals(1, failures.size)

    storage.failOnWrite = false
    repository.update { it.copy(fontSize = 16) }
    advanceUntilIdle()

    assertEquals(1, storage.writes.size)
    assertEquals(16, decodeSettings(storage.writes.single())?.fontSize)
    assertEquals(1, failures.size)
  }
}

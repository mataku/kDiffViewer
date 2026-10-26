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
import kotlin.test.assertFalse
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
  fun missingFileGivesDefaultsWithoutError() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage(content = null)
    val repository = repository(storage)
    advanceUntilIdle()

    assertEquals(Settings(), repository.settings.value)
    assertFalse(repository.loadError)
    assertTrue(storage.writes.isEmpty())
  }

  @Test
  fun malformedFileGivesDefaultsAndError() = runTest(StandardTestDispatcher()) {
    val repository = repository(FakeSettingsStorage(content = "{"))

    assertEquals(Settings(), repository.settings.value)
    assertTrue(repository.loadError)
  }

  @Test
  fun throwingReadGivesDefaultsAndError() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage(failOnRead = true)
    val repository = repository(storage)

    assertEquals(Settings(), repository.settings.value)
    assertTrue(repository.loadError)

    repository.update { it.copy(fontSize = 14) }
    advanceUntilIdle()

    assertEquals(listOf("backup", "write"), storage.events)
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
    assertEquals(14, SettingsCodec.decode(storage.writes.single())?.fontSize)
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
    assertEquals(13, SettingsCodec.decode(storage.writes.single())?.fontSize)
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
  fun firstSaveAfterLoadErrorBacksUpOnce() = runTest(StandardTestDispatcher()) {
    val storage = FakeSettingsStorage(content = "{")
    val repository = repository(storage)

    repository.update { it.copy(fontSize = 14) }
    advanceUntilIdle()
    repository.update { it.copy(fontSize = 16) }
    advanceUntilIdle()

    assertEquals(1, storage.backupCalls)
    assertEquals(listOf("backup", "write", "write"), storage.events)
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
    assertEquals(16, SettingsCodec.decode(storage.writes.single())?.fontSize)
    assertEquals(1, failures.size)
  }
}

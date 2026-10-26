package io.github.mataku.kdiffviewer.app.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

interface SettingsStorage {
  fun read(): String?
  fun write(text: String)
  fun backup()
}

sealed interface SettingsLoad {
  data object Ok : SettingsLoad
  data object PartiallyReset : SettingsLoad
  data object Unreadable : SettingsLoad
}

class SettingsRepository(
  private val storage: SettingsStorage,
  scope: CoroutineScope,
  ioDispatcher: CoroutineDispatcher,
  private val onWriteFailure: (Throwable) -> Unit = { it.printStackTrace() },
) {
  private val state: MutableStateFlow<Settings>
  val settings: StateFlow<Settings>
  val load: SettingsLoad

  private val pending = Channel<Settings>(Channel.CONFLATED)
  private var needsBackup: Boolean
  private var lastWritten: String? = null
  private val consumer: Job

  init {
    var readFailed = false
    val text = try {
      storage.read()
    } catch (e: Exception) {
      readFailed = true
      null
    }
    val result = text?.let(SettingsCodec::decode)
    load = when {
      readFailed || result is DecodeResult.Invalid -> SettingsLoad.Unreadable
      result is DecodeResult.Decoded && result.partial -> SettingsLoad.PartiallyReset
      else -> SettingsLoad.Ok
    }
    needsBackup = load == SettingsLoad.PartiallyReset
    state = MutableStateFlow((result as? DecodeResult.Decoded)?.settings ?: Settings())
    settings = state
    consumer = scope.launch(ioDispatcher) {
      for (value in pending) {
        persist(value)
      }
    }
  }

  fun update(transform: (Settings) -> Settings) {
    while (true) {
      val current = state.value
      val next = transform(current).normalized()
      if (next == current) return
      if (state.compareAndSet(current, next)) {
        if (load != SettingsLoad.Unreadable) pending.trySend(next)
        return
      }
    }
  }

  suspend fun flush(timeoutMillis: Long = 1000) {
    pending.close()
    withTimeoutOrNull(timeoutMillis) { consumer.join() }
  }

  private fun persist(value: Settings) {
    try {
      val text = SettingsCodec.encode(value)
      if (text == lastWritten) return
      if (needsBackup) {
        storage.backup()
        needsBackup = false
      }
      storage.write(text)
      lastWritten = text
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      onWriteFailure(e)
    }
  }
}

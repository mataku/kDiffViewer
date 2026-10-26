package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.app.ViewerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

class ReviewIntegration(
  private val registry: SessionRegistry,
  parentContext: CoroutineContext,
  private val ioContext: CoroutineContext,
  private val handle: suspend (String) -> String,
  private val warn: (String) -> Unit = System.err::println,
) {
  private val scope = CoroutineScope(SupervisorJob() + parentContext)
  private var server: ReviewSocketServer? = null
  private var closed = false

  @Synchronized
  fun start(): Boolean {
    if (closed) return false
    check(server == null) { "Integration already started" }
    val socket = registry.prepare()
    if (socket == null) {
      warn("kdv integration disabled: socket path too long")
      return false
    }
    server = ReviewSocketServer(socket, scope, ioContext, handle).also { it.start() }
    return true
  }

  @Synchronized
  fun publish(repoRoot: Path?) {
    if (server == null) return
    registry.publish(repoRoot)
  }

  suspend fun publishRoots(states: Flow<ViewerUiState>) {
    states.map { it.root ?: it.repo }.distinctUntilChanged().collect { root ->
      withContext(ioContext) {
        try {
          publish(root?.let { Path.of(it.value) })
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          warn("kdv integration: could not publish ${root?.value}: ${e.message}")
        }
      }
    }
  }

  @Synchronized
  fun close() {
    closed = true
    server?.close()
    server = null
    scope.cancel()
    registry.remove()
  }
}

package io.github.mataku.kdiffviewer.app.review

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

  @Synchronized
  fun start(): Boolean {
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

  @Synchronized
  fun close() {
    server?.close()
    server = null
    scope.cancel()
    registry.remove()
  }
}

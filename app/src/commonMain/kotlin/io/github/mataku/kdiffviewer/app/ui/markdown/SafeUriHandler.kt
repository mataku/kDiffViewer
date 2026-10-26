package io.github.mataku.kdiffviewer.app.ui.markdown

import androidx.compose.ui.platform.UriHandler
import kotlin.coroutines.cancellation.CancellationException

class SafeUriHandler(
  private val delegate: UriHandler,
  private val onFailure: (Throwable) -> Unit = { it.printStackTrace() },
) : UriHandler {
  override fun openUri(uri: String) {
    try {
      delegate.openUri(uri)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      onFailure(e)
    }
  }
}

package io.github.mataku.kdiffviewer.app.ui.markdown

import androidx.compose.ui.platform.UriHandler
import java.net.URI
import java.net.URISyntaxException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SafeUriHandlerTest {
  private class Delegate(private val failure: Throwable? = null) : UriHandler {
    val opened = mutableListOf<String>()

    override fun openUri(uri: String) {
      failure?.let { throw it }
      opened += uri
    }
  }

  @Test
  fun delegatesValidUri() {
    val delegate = Delegate()
    SafeUriHandler(delegate).openUri("https://e.com")
    assertEquals(listOf("https://e.com"), delegate.opened)
  }

  @Test
  fun reportsDelegateFailureAndReturns() {
    val failures = mutableListOf<Throwable>()
    val handler = SafeUriHandler(Delegate(URISyntaxException("x", "bad")), failures::add)
    handler.openUri("https://e.com/{a}")
    assertEquals(1, failures.size)
    assertTrue(failures.single() is URISyntaxException)
  }

  @Test
  fun realUriRejectionIsContained() {
    val failures = mutableListOf<Throwable>()
    val throwing = object : UriHandler {
      override fun openUri(uri: String) {
        URI(uri)
      }
    }
    SafeUriHandler(throwing, failures::add).openUri("https://api.github.com/repos/{owner}/{repo}")
    assertEquals(1, failures.size)
  }

  @Test
  fun rethrowsCancellation() {
    val handler = SafeUriHandler(Delegate(CancellationException("c")), {})
    assertFailsWith<CancellationException> { handler.openUri("https://e.com") }
  }
}

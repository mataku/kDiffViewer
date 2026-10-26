package io.github.mataku.kdiffviewer.app.highlight

import androidx.compose.ui.text.AnnotatedString
import io.github.mataku.kdiffviewer.app.ui.DarkSyntaxTheme
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeHighlighterJvmTest {
  @Test
  fun unloadableLanguageReturnsNull() = runTest {
    var highlightCalls = 0
    val h = CodeHighlighter(
      LanguageResolver<String>(mapOf(LanguageId.Kotlin to { throw UnsatisfiedLinkError("no lib") })) {},
      { _, _, _ ->
        highlightCalls++
        AnnotatedString("x")
      },
      StandardTestDispatcher(testScheduler),
    )
    assertNull(h.highlight("x", "kt", DarkSyntaxTheme))
    assertTrue(highlightCalls == 0)
  }

  @Test
  fun languageLoadAndHighlightRunOnInjectedDispatcher() = runTest {
    val executor = Executors.newSingleThreadExecutor { Thread(it, "highlighter-test") }
    try {
      val threads = mutableListOf<String>()
      val h = CodeHighlighter(
        LanguageResolver(
          mapOf(
            LanguageId.Kotlin to {
              threads += Thread.currentThread().name
              "Kotlin"
            },
          ),
        ),
        { _, _, _ ->
          threads += Thread.currentThread().name
          AnnotatedString("x")
        },
        executor.asCoroutineDispatcher(),
      )
      h.highlight("x", "kt", DarkSyntaxTheme)
      assertEquals(listOf("highlighter-test", "highlighter-test"), threads)
    } finally {
      executor.shutdown()
    }
  }
}

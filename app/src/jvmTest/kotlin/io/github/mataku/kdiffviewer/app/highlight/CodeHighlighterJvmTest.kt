package io.github.mataku.kdiffviewer.app.highlight

import androidx.compose.ui.text.AnnotatedString
import io.github.mataku.kdiffviewer.app.ui.DarkSyntaxTheme
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
}

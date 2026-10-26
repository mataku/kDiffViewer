package io.github.mataku.kdiffviewer.app.highlight

import androidx.compose.ui.text.AnnotatedString
import io.github.mataku.kdiffviewer.app.ui.DarkSyntaxTheme
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CodeHighlighterTest {
  private val calls = mutableListOf<Pair<String, String>>()
  private val failures = mutableListOf<Throwable>()
  private val result = AnnotatedString("highlighted")

  private fun TestScope.highlighter(
    accessors: Map<LanguageId, () -> String> = LanguageId.entries.associateWith { id -> { id.name } },
    highlight: (String, String) -> AnnotatedString = { code, language ->
      calls += code to language
      result
    },
  ) = CodeHighlighter(
    LanguageResolver(accessors),
    { code, language, _ -> highlight(code, language) },
    StandardTestDispatcher(testScheduler),
    { failures += it },
  )

  @Test
  fun highlightsKnownLanguage() = runTest {
    val h = highlighter()
    assertSame(result, h.highlight("val a = 1", "kt", DarkSyntaxTheme))
    assertEquals(listOf("val a = 1" to "Kotlin"), calls)
  }

  @Test
  fun unknownOrMissingInfoReturnsNull() = runTest {
    val h = highlighter()
    assertNull(h.highlight("x", null, DarkSyntaxTheme))
    assertNull(h.highlight("x", "cobol", DarkSyntaxTheme))
    assertTrue(calls.isEmpty())
  }

  @Test
  fun throwingHighlighterReturnsNullAndReports() = runTest {
    val h = highlighter(highlight = { _, _ -> throw IllegalStateException("boom") })
    assertNull(h.highlight("x", "kt", DarkSyntaxTheme))
    assertEquals(1, failures.size)
    assertTrue(failures[0] is IllegalStateException)
    assertEquals("boom", failures[0].message)
  }
}

package io.github.mataku.kdiffviewer.app.ui.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.highlight.CodeHighlighter
import io.github.mataku.kdiffviewer.app.highlight.LanguageId
import io.github.mataku.kdiffviewer.app.highlight.LanguageResolver
import io.github.mataku.kdiffviewer.app.highlight.LocalCodeHighlighter
import io.github.mataku.kdiffviewer.app.ui.KdvTheme
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownBodyTest {
  private class FakeUriHandler : UriHandler {
    val opened = mutableListOf<String>()
    override fun openUri(uri: String) {
      opened += uri
    }
  }

  private val marker = SpanStyle(color = Color.Magenta)

  private fun highlighter() = CodeHighlighter(
    LanguageResolver(LanguageId.entries.associateWith { id -> { id.name } }),
    { code, _, _ -> AnnotatedString(code, listOf(AnnotatedString.Range(marker, 0, code.length))) },
    Dispatchers.Unconfined,
  )

  @Composable
  private fun Themed(handler: UriHandler = FakeUriHandler(), content: @Composable () -> Unit) {
    KdvTheme(darkTheme = false) {
      CompositionLocalProvider(LocalUriHandler provides handler, content = content)
    }
  }

  private fun SemanticsNodeInteraction.annotatedText(): AnnotatedString = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.single()

  @Test
  fun hidesMarkers() = runComposeUiTest {
    setContent { Themed { MarkdownBody("# Title\n\n**bold** and `code`") } }
    onNodeWithText("Title").assertIsDisplayed()
    listOf("**", "#", "`").forEach { onAllNodesWithText(it, substring = true).assertCountEquals(0) }
  }

  @Test
  fun rendersListBulletsAndNumbers() = runComposeUiTest {
    setContent { Themed { MarkdownBody("- a\n- b\n\ntext\n\n3. x") } }
    onAllNodesWithText("•").assertCountEquals(2)
    onNodeWithText("3.").assertIsDisplayed()
  }

  @Test
  fun clickingAllowedLinkOpensUri() = runComposeUiTest {
    val handler = FakeUriHandler()
    setContent { Themed(handler) { MarkdownBody("[docs](https://e.com)") } }
    onNodeWithText("docs").performClick()
    assertEquals(listOf("https://e.com"), handler.opened)
  }

  @Test
  fun disallowedLinkDoesNothing() = runComposeUiTest {
    val handler = FakeUriHandler()
    setContent { Themed(handler) { MarkdownBody("[x](javascript:alert(1))") } }
    onNodeWithText("x").performClick()
    assertTrue(handler.opened.isEmpty())
  }

  @Test
  fun codeBlockShowsHighlightedTextFromHighlighter() = runComposeUiTest {
    setContent {
      Themed { CompositionLocalProvider(LocalCodeHighlighter provides highlighter()) { MarkdownBody("```kt\nval a = 1\n```") } }
    }
    waitUntil { onAllNodes(hasText("val a = 1")).fetchSemanticsNodes().any { n -> n.config.getOrNull(SemanticsProperties.Text)?.single()?.spanStyles?.any { it.item == marker } == true } }
    assertTrue(onNodeWithText("val a = 1").annotatedText().spanStyles.any { it.item == marker })
  }

  @Test
  fun codeBlockWithoutHighlighterIsPlain() = runComposeUiTest {
    setContent { Themed { MarkdownBody("```kt\nval a = 1\n```") } }
    assertTrue(onNodeWithText("val a = 1").annotatedText().spanStyles.isEmpty())
  }

  @Test
  fun wideCodeLineScrollsInsideBlock() = runComposeUiTest {
    val line = "x".repeat(500)
    setContent {
      Themed { Box(Modifier.width(300.dp)) { MarkdownBody("```\n$line\n```", Modifier.testTag("md")) } }
    }
    val width = onNodeWithTag("md").fetchSemanticsNode().size.width
    with(density) { assertTrue(width <= 300.dp.roundToPx(), "width was $width") }
  }

  @Test
  fun softBreaksRenderAsLines() = runComposeUiTest {
    setContent { Themed { MarkdownBody("one\ntwo") } }
    onNodeWithText("one\ntwo").assertIsDisplayed()
  }
}

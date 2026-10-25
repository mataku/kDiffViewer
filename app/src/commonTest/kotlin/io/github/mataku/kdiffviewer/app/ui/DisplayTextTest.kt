package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.compose.highlight.core.SyntaxTheme
import io.github.mataku.kdiffviewer.core.model.Hunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DisplayTextTest {
  @Test
  fun tabAtColumnZeroExpandsToEightSpaces() {
    assertEquals("        x", expandTabs("\tx"))
  }

  @Test
  fun tabMidLineExpandsToNextMultipleOfEight() {
    assertEquals("abc     d", expandTabs("abc\td"))
    assertEquals("abcdefgh        i", expandTabs("abcdefgh\ti"))
  }

  @Test
  fun multipleTabsEachAdvanceToNextStop() {
    assertEquals("a       b       c", expandTabs("a\tb\tc"))
    assertEquals("                x", expandTabs("\t\tx"))
  }

  @Test
  fun textWithoutTabsIsReturnedAsIs() {
    val text = "no tabs here"
    assertSame(text, expandTabs(text))
  }

  @Test
  fun hunkLabelShowsFullRangeFollowedByFunctionName() {
    assertEquals("@@ -1,2 +3,4 @@ fun main()", hunkHeaderLabel(Hunk(1, 2, 3, 4, " fun main()", emptyList())))
  }

  @Test
  fun hunkLabelWithoutFunctionNameIsJustTheRange() {
    assertEquals("@@ -0,0 +1,5 @@", hunkHeaderLabel(Hunk(0, 0, 1, 5, "", emptyList())))
  }

  private val red = SpanStyle(color = Color.Red)
  private val blue = SpanStyle(color = Color.Blue)
  private val theme = SyntaxTheme(keyword = red, string = blue)

  @Test
  fun lineWithoutTabsKeepsOffsets() {
    val result = displayText("fun x", listOf(HighlightSpan(0, 3, "keyword")), theme)
    assertEquals("fun x", result.text)
    assertEquals(listOf(AnnotatedString.Range(red, 0, 3)), result.spanStyles)
  }

  @Test
  fun tabBeforeSpanShiftsIt() {
    val result = displayText("\tfun", listOf(HighlightSpan(1, 4, "keyword")), theme)
    assertEquals("        fun", result.text)
    assertEquals(listOf(AnnotatedString.Range(red, 8, 11)), result.spanStyles)
  }

  @Test
  fun tabInsideSpanWidensIt() {
    val result = displayText("a\tb", listOf(HighlightSpan(0, 3, "string")), theme)
    assertEquals("a       b", result.text)
    assertEquals(listOf(AnnotatedString.Range(blue, 0, 9)), result.spanStyles)
  }

  @Test
  fun spanEndingAtLineEnd() {
    val result = displayText("ab\t", listOf(HighlightSpan(2, 3, "string")), theme)
    assertEquals(listOf(AnnotatedString.Range(blue, 2, 8)), result.spanStyles)
  }

  @Test
  fun unresolvedCaptureIsSkipped() {
    val spans = listOf(HighlightSpan(0, 1, "variable"), HighlightSpan(0, 1, "keyword"))
    assertEquals(listOf(AnnotatedString.Range(red, 0, 1)), displayText("ab", spans, theme).spanStyles)
  }

  @Test
  fun spanOrderIsKept() {
    val spans = listOf(HighlightSpan(0, 5, "string"), HighlightSpan(1, 2, "keyword"))
    assertEquals(
      listOf(AnnotatedString.Range(blue, 0, 5), AnnotatedString.Range(red, 1, 2)),
      displayText("abcde", spans, theme).spanStyles,
    )
  }

  @Test
  fun baseStyleIsNotApplied() {
    val green = SpanStyle(color = Color.Green)
    val themed = SyntaxTheme(keyword = red, baseStyle = green)
    val result = displayText("fun", listOf(HighlightSpan(0, 3, "keyword")), themed)
    assertEquals(listOf(AnnotatedString.Range(red, 0, 3)), result.spanStyles)
  }
}

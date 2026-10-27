package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.github.mataku.compose.highlight.core.HighlightSpan
import io.github.mataku.compose.highlight.core.SyntaxTheme
import io.github.mataku.kdiffviewer.core.model.Hunk
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DisplayTextTest {
  @Test
  fun tabAtColumnZeroExpandsToEightSpaces() {
    assertEquals("        x", expandTabs("\tx", 8))
  }

  @Test
  fun tabMidLineExpandsToNextMultipleOfEight() {
    assertEquals("abc     d", expandTabs("abc\td", 8))
    assertEquals("abcdefgh        i", expandTabs("abcdefgh\ti", 8))
  }

  @Test
  fun multipleTabsEachAdvanceToNextStop() {
    assertEquals("a       b       c", expandTabs("a\tb\tc", 8))
    assertEquals("                x", expandTabs("\t\tx", 8))
  }

  @Test
  fun textWithoutTabsIsReturnedAsIs() {
    val text = "no tabs here"
    assertSame(text, expandTabs(text, 8))
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
    val result = displayText("fun x", listOf(HighlightSpan(0, 3, "keyword")), theme, 8)
    assertEquals("fun x", result.text)
    assertEquals(listOf(AnnotatedString.Range(red, 0, 3)), result.spanStyles)
  }

  @Test
  fun tabBeforeSpanShiftsIt() {
    val result = displayText("\tfun", listOf(HighlightSpan(1, 4, "keyword")), theme, 8)
    assertEquals("        fun", result.text)
    assertEquals(listOf(AnnotatedString.Range(red, 8, 11)), result.spanStyles)
  }

  @Test
  fun tabInsideSpanWidensIt() {
    val result = displayText("a\tb", listOf(HighlightSpan(0, 3, "string")), theme, 8)
    assertEquals("a       b", result.text)
    assertEquals(listOf(AnnotatedString.Range(blue, 0, 9)), result.spanStyles)
  }

  @Test
  fun spanEndingAtLineEnd() {
    val result = displayText("ab\t", listOf(HighlightSpan(2, 3, "string")), theme, 8)
    assertEquals(listOf(AnnotatedString.Range(blue, 2, 8)), result.spanStyles)
  }

  @Test
  fun unresolvedCaptureIsSkipped() {
    val spans = listOf(HighlightSpan(0, 1, "variable"), HighlightSpan(0, 1, "keyword"))
    assertEquals(listOf(AnnotatedString.Range(red, 0, 1)), displayText("ab", spans, theme, 8).spanStyles)
  }

  @Test
  fun spanOrderIsKept() {
    val spans = listOf(HighlightSpan(0, 5, "string"), HighlightSpan(1, 2, "keyword"))
    assertEquals(
      listOf(AnnotatedString.Range(blue, 0, 5), AnnotatedString.Range(red, 1, 2)),
      displayText("abcde", spans, theme, 8).spanStyles,
    )
  }

  @Test
  fun baseStyleIsNotApplied() {
    val green = SpanStyle(color = Color.Green)
    val themed = SyntaxTheme(keyword = red, baseStyle = green)
    val result = displayText("fun", listOf(HighlightSpan(0, 3, "keyword")), themed, 8)
    assertEquals(listOf(AnnotatedString.Range(red, 0, 3)), result.spanStyles)
  }

  @Test
  fun tabAtNonZeroColumnInsideSpanWidensIt() {
    val result = displayText("ab\tc", listOf(HighlightSpan(0, 4, "string")), theme, 8)
    assertEquals("ab      c", result.text)
    assertEquals(listOf(AnnotatedString.Range(blue, 0, 9)), result.spanStyles)
  }

  @Test
  fun displayTextMatchesExpandTabs() {
    listOf("", "abc", "ab\tc", "\ta\tb", "\t\t", "abcdefgh\ti").forEach {
      assertEquals(expandTabs(it, 8), displayText(it, emptyList(), theme, 8).text)
    }
  }

  @Test
  fun repoNameIsLastPathSegment() {
    assertEquals("kDiffViewer", repoName(RepoPath("/Users/me/src/kDiffViewer")))
  }

  @Test
  fun repoNameIgnoresTrailingSlash() {
    assertEquals("repo", repoName(RepoPath("/work/repo/")))
  }

  @Test
  fun repoNameOfRootIsThePathItself() {
    assertEquals("/", repoName(RepoPath("/")))
  }

  @Test
  fun tabWidthTwoAndFour() {
    assertEquals("a b", expandTabs("a\tb", 2))
    assertEquals("a   b", expandTabs("a\tb", 4))
  }

  @Test
  fun noTabsIsIdentity() {
    assertEquals(2, displayToSource("abc", 2, 4))
    assertEquals(2, sourceToDisplay("abc", 2, 4))
  }

  @Test
  fun sourceToDisplayExpandsTab() {
    val text = "a\tb"
    assertEquals(4, sourceToDisplay(text, 2, 4))
    assertEquals(5, sourceToDisplay(text, 3, 4))
  }

  @Test
  fun displayInsideTabSnapsByHalf() {
    val text = "a\tb"
    assertEquals(1, displayToSource(text, 1, 4))
    assertEquals(1, displayToSource(text, 2, 4))
    assertEquals(2, displayToSource(text, 3, 4))
    assertEquals(2, displayToSource(text, 4, 4))
  }

  @Test
  fun lineEndingInTab() {
    assertEquals(2, displayToSource("a\t", 4, 4))
  }

  @Test
  fun roundTrip() {
    val text = "\tx\t\ty"
    for (index in 0..text.length) {
      assertEquals(index, displayToSource(text, sourceToDisplay(text, index, 8), 8))
    }
  }

  @Test
  fun clampsOutOfRange() {
    assertEquals(2, displayToSource("ab", 99, 4))
    assertEquals(0, displayToSource("ab", -1, 4))
    assertEquals(3, displayToSource("a\tb", 99, 4))
    assertEquals(5, sourceToDisplay("a\tb", 99, 4))
    assertEquals(0, sourceToDisplay("a\tb", -1, 4))
  }

  @Test
  fun displayTextOffsetsFollowTabWidth() {
    val result = displayText("\tfun", listOf(HighlightSpan(1, 4, "keyword")), theme, 4)
    assertEquals(listOf(AnnotatedString.Range(red, 4, 7)), result.spanStyles)
  }
}

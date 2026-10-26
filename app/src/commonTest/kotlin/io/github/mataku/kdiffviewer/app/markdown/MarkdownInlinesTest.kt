package io.github.mataku.kdiffviewer.app.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownInlinesTest {
  private fun inline(src: String) = (parseMarkdown(src).single() as MdBlock.Paragraph).text

  private fun span(
    kind: MdSpanKind,
    start: Int,
    end: Int,
    url: String? = null,
  ) = MdSpan(kind, start, end, url)

  @Test
  fun strongEmphasisStrike() {
    val result = inline("a **b** *c* ~~d~~")
    assertEquals("a b c d", result.text)
    assertEquals(
      setOf(span(MdSpanKind.Strong, 2, 3), span(MdSpanKind.Emphasis, 4, 5), span(MdSpanKind.Strikethrough, 6, 7)),
      result.spans.toSet(),
    )
    assertEquals(3, result.spans.size)
  }

  @Test
  fun nestedStrongInEmphasis() {
    val result = inline("*a **b***")
    assertEquals("a b", result.text)
    assertEquals(setOf(span(MdSpanKind.Emphasis, 0, 3), span(MdSpanKind.Strong, 2, 3)), result.spans.toSet())
    assertEquals(2, result.spans.size)
  }

  @Test
  fun codeSpanKeepsContentVerbatim() {
    val result = inline("x `a*b*` y")
    assertEquals("x a*b* y", result.text)
    assertEquals(listOf(span(MdSpanKind.Code, 2, 6)), result.spans)
  }

  @Test
  fun inlineLink() {
    val result = inline("see [docs](https://e.com/a)")
    assertEquals("see docs", result.text)
    assertEquals(listOf(span(MdSpanKind.Link, 4, 8, "https://e.com/a")), result.spans)
  }

  @Test
  fun linkTextKeepsInnerStyles() {
    val result = inline("[**b** `c`](https://e.com)")
    assertEquals("b c", result.text)
    assertEquals(
      setOf(span(MdSpanKind.Link, 0, 3, "https://e.com"), span(MdSpanKind.Strong, 0, 1), span(MdSpanKind.Code, 2, 3)),
      result.spans.toSet(),
    )
    assertEquals(3, result.spans.size)
  }

  @Test
  fun autolinks() {
    val angle = inline("<https://e.com>")
    assertEquals("https://e.com", angle.text)
    assertEquals(listOf(span(MdSpanKind.Link, 0, 13, "https://e.com")), angle.spans)
    val bare = inline("go https://e.com now")
    assertEquals("go https://e.com now", bare.text)
    assertEquals(listOf(span(MdSpanKind.Link, 3, 16, "https://e.com")), bare.spans)
  }

  @Test
  fun mailtoAllowed() {
    val result = inline("[m](mailto:a@b.c)")
    assertEquals("m", result.text)
    assertEquals(listOf(span(MdSpanKind.Link, 0, 1, "mailto:a@b.c")), result.spans)
  }

  @Test
  fun disallowedSchemesAreText() {
    for (src in listOf("[x](javascript:alert(1))", "[x](file:///etc/passwd)", "[x](vscode://open)")) {
      val result = inline(src)
      assertEquals("x", result.text, src)
      assertEquals(emptyList(), result.spans, src)
    }
    val upper = inline("[x](HTTPS://e.com)")
    assertEquals(listOf(span(MdSpanKind.Link, 0, 1, "HTTPS://e.com")), upper.spans)
  }

  @Test
  fun referenceLinks() {
    val blocks = parseMarkdown("[a][r] and [r]\n\n[r]: https://e.com")
    assertEquals(1, blocks.size)
    val result = (blocks.single() as MdBlock.Paragraph).text
    assertEquals("a and r", result.text)
    assertEquals(
      listOf(span(MdSpanKind.Link, 0, 1, "https://e.com"), span(MdSpanKind.Link, 6, 7, "https://e.com")),
      result.spans,
    )
    val missing = inline("[a][missing]")
    assertEquals("[a][missing]", missing.text)
    assertEquals(emptyList(), missing.spans)
  }

  @Test
  fun imageBecomesAltLink() {
    val result = inline("![logo](https://e.com/l.png)")
    assertEquals("logo", result.text)
    assertEquals(listOf(span(MdSpanKind.Link, 0, 4, "https://e.com/l.png")), result.spans)
  }

  @Test
  fun softBreakIsNewline() {
    assertEquals("line one\nline two", inline("line one\nline two").text)
  }

  @Test
  fun hardBreak() {
    assertEquals("a\nb", inline("a  \nb").text)
    assertEquals("a\nb", inline("a\\\nb").text)
  }

  @Test
  fun escapesAndEntities() {
    val result = inline("\\*x\\* &amp; &copy;")
    assertEquals("*x* & ©", result.text)
    assertEquals(emptyList(), result.spans)
  }

  @Test
  fun rawInlineHtmlIsText() {
    val result = inline("a <b>x</b>")
    assertEquals("a <b>x</b>", result.text)
    assertEquals(emptyList(), result.spans)
  }

  @Test
  fun cjkAndEmojiOffsets() {
    val result = inline("日本語 **強調** 😀 `コード` [リンク](https://e.com)")
    assertEquals("日本語 強調 😀 コード リンク", result.text)
    val byKind = result.spans.associateBy { it.kind }
    assertEquals("強調", result.text.substring(byKind.getValue(MdSpanKind.Strong).let { it.start }, byKind.getValue(MdSpanKind.Strong).end))
    assertEquals("コード", result.text.substring(byKind.getValue(MdSpanKind.Code).start, byKind.getValue(MdSpanKind.Code).end))
    assertEquals("リンク", result.text.substring(byKind.getValue(MdSpanKind.Link).start, byKind.getValue(MdSpanKind.Link).end))
  }

  @Test
  fun headingInlines() {
    assertEquals(
      listOf(MdBlock.Heading(1, MdInline("a b", listOf(span(MdSpanKind.Emphasis, 2, 3))))),
      parseMarkdown("# a *b*"),
    )
  }

  @Test
  fun deeplyNestedEmphasisDoesNotThrow() {
    val src = "*_".repeat(200_000) + "x"
    val blocks = parseMarkdown(src)
    assertEquals(true, blocks.isNotEmpty())
    assertEquals(true, blocks.joinToString { it.toString() }.contains("x"))
  }

  @Test
  fun deeplyNestedBracketsDoNotThrow() {
    val src = "[".repeat(200_000) + "x" + "]".repeat(200_000)
    val blocks = parseMarkdown(src)
    assertEquals(true, blocks.joinToString { it.toString() }.contains("x"))
  }

  @Test
  fun nestingPastInlineCapKeepsTextWithoutSpans() {
    val depth = 100
    val src = "*a ".repeat(depth) + "x" + "*".repeat(depth)
    val result = inline(src)
    assertEquals(true, result.text.contains("x"))
    assertEquals(true, result.spans.size <= 33)
  }
}

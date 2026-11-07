package io.github.mataku.kdiffviewer.app.markdown

import io.github.mataku.kdiffviewer.protocol.controlMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SourceBlocksTest {
  private val rlo = Char(0x202E)

  private fun blocks(source: String) = assertIs<SourceParse.Blocks>(parseMarkdownSource(source)).blocks

  private fun SourceBlock.text(): String = when (val leaf = block) {
    is MdBlock.Paragraph -> leaf.text.text
    is MdBlock.Heading -> leaf.text.text
    is MdBlock.CodeBlock -> leaf.code
    else -> error("no text in $leaf")
  }

  private fun List<SourceBlock>.ranges() = map { it.startLine..it.endLine }

  private val mixed = "# T\n\npara\nsecond\n\n```\ncode\n```\n"

  @Test
  fun lineRanges() {
    assertEquals(listOf(1..1, 3..4, 6..8), blocks(mixed).ranges())
  }

  @Test
  fun crlfKeepsLineNumbers() {
    assertEquals(listOf(1..1, 3..4, 6..8), blocks(mixed.replace("\n", "\r\n")).ranges())
  }

  @Test
  fun loneCarriageReturnKeepsLineNumbers() {
    val result = blocks("a\rx\n\nevil\n\nc\n")
    assertEquals(listOf(1..1, 3..3, 5..5), result.ranges())
    assertEquals("a${controlMarker('\r')}x", result[0].text())
  }

  @Test
  fun listItemsFlatten() {
    val result = blocks("- a\n- b\n  - c\n1. d\n")
    assertEquals(listOf("a", "b", "c", "d"), result.map { it.text() })
    assertEquals(listOf("•", "•", "•", "1."), result.map { it.marker })
    assertEquals(listOf(1, 1, 2, 1), result.map { it.indent })
    assertEquals(listOf(1..1, 2..2, 3..3, 4..4), result.ranges())
  }

  @Test
  fun checkboxMarker() {
    assertEquals(listOf("☑", "☐"), blocks("- [x] a\n- [ ] b\n").map { it.marker })
  }

  @Test
  fun secondItemBlockHasNoMarker() {
    val result = blocks("- a\n\n  b\n")
    assertEquals(listOf("a", "b"), result.map { it.text() })
    assertEquals("•", result[0].marker)
    assertNull(result[1].marker)
  }

  @Test
  fun quoteFlattens() {
    val result = blocks("> a\n>\n> b\n")
    assertEquals(listOf("a", "b"), result.map { it.text() })
    assertEquals(listOf(1, 1), result.map { it.quoteDepth })
    assertEquals(listOf(1..1, 3..3), result.ranges())
  }

  @Test
  fun tableIsSource() {
    val table = "| a | b |\n|---|---|\n| 1 | 2 |"
    assertEquals(listOf<MdBlock>(MdBlock.CodeBlock(null, table)), blocks("$table\n").map { it.block })
  }

  @Test
  fun htmlIsSource() {
    assertEquals(listOf<MdBlock>(MdBlock.CodeBlock(null, "<!-- hidden -->")), blocks("<!-- hidden -->\n").map { it.block })
  }

  @Test
  fun linkDefinitionIsSource() {
    val result = blocks("[a]: https://x\n")
    assertEquals(listOf<MdBlock>(MdBlock.CodeBlock(null, "[a]: https://x")), result.map { it.block })
    assertEquals(listOf(1..1), result.ranges())
  }

  @Test
  fun marksControlsAndKeepsNewlines() {
    val marked = controlMarker(rlo)
    assertEquals("a${marked}b\nc", blocks("a${rlo}b\nc\n").single().text())
  }

  @Test
  fun markingKeepsSpanOffsets() {
    val paragraph = assertIs<MdBlock.Paragraph>(blocks("x **b$rlo** y\n").single().block)
    val span = paragraph.text.spans.single()
    assertEquals("b${controlMarker(rlo)}", paragraph.text.text.substring(span.start, span.end))
  }

  @Test
  fun marksCodeBlocks() {
    assertEquals("a${controlMarker(rlo)}", blocks("```\na$rlo\n```\n").single().text())
  }

  @Test
  fun tooLarge() {
    assertEquals(SourceParse.TooLarge, parseMarkdownSource("a".repeat(RENDER_SOURCE_LIMIT + 1)))
  }

  @Test
  fun tooDeep() {
    assertEquals(SourceParse.Failed, parseMarkdownSource("> ".repeat(100) + "a\n"))
  }

  @Test
  fun parserFailure() {
    assertEquals(SourceParse.Failed, MarkdownBodyParser(onFailure = {}, buildTree = { error("boom") }).parseSource("x"))
  }

  @Test
  fun parsesAtCap() {
    val unit = "# Title\n\nSome *text* with a [link](https://example.com).\n\n- item one\n- item two\n  - nested\n\n"
    val source = unit.repeat(RENDER_SOURCE_LIMIT / unit.length)
    assertIs<SourceParse.Blocks>(parseMarkdownSource(source))
  }
}

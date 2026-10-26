package io.github.mataku.kdiffviewer.app.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownBlocksTest {
  private fun inline(text: String) = MdInline(text, emptyList())

  private fun para(text: String) = MdBlock.Paragraph(inline(text))

  private fun item(
    vararg blocks: MdBlock,
    checked: Boolean? = null,
  ) = ListItem(checked, blocks.toList())

  @Test
  fun paragraphsSplitOnBlankLines() {
    assertEquals(listOf(para("a"), para("b")), parseMarkdown("a\n\nb"))
  }

  @Test
  fun atxAndSetextHeadings() {
    assertEquals(listOf(MdBlock.Heading(1, inline("T"))), parseMarkdown("# T"))
    assertEquals(listOf(MdBlock.Heading(3, inline("T"))), parseMarkdown("### T"))
    assertEquals(listOf(MdBlock.Heading(1, inline("T"))), parseMarkdown("T\n==="))
    assertEquals(listOf(MdBlock.Heading(2, inline("T"))), parseMarkdown("T\n---"))
  }

  @Test
  fun unorderedListWithNesting() {
    val expected =
      MdBlock.ListBlock(
        false,
        1,
        listOf(
          item(para("a"), MdBlock.ListBlock(false, 1, listOf(item(para("b"))))),
          item(para("c")),
        ),
      )
    assertEquals(listOf(expected), parseMarkdown("- a\n  - b\n- c"))
  }

  @Test
  fun orderedListKeepsStart() {
    assertEquals(
      listOf(MdBlock.ListBlock(true, 3, listOf(item(para("a")), item(para("b"))))),
      parseMarkdown("3. a\n4. b"),
    )
  }

  @Test
  fun taskItems() {
    assertEquals(
      listOf(MdBlock.ListBlock(false, 1, listOf(item(para("a"), checked = true), item(para("b"), checked = false)))),
      parseMarkdown("- [x] a\n- [ ] b"),
    )
  }

  @Test
  fun blockQuote() {
    assertEquals(listOf(MdBlock.Quote(listOf(para("a\nb")))), parseMarkdown("> a\n> b"))
  }

  @Test
  fun fencedCode() {
    assertEquals(listOf(MdBlock.CodeBlock("kotlin", "val a = 1")), parseMarkdown("```kotlin\nval a = 1\n```"))
    assertEquals(listOf(MdBlock.CodeBlock(null, "val a = 1")), parseMarkdown("```\nval a = 1\n```"))
    assertEquals(listOf(MdBlock.CodeBlock("kotlin title=x", "v")), parseMarkdown("```kotlin title=x\nv\n```"))
  }

  @Test
  fun fencedCodeKeepsBlankLines() {
    assertEquals(listOf(MdBlock.CodeBlock(null, "a\n\nb")), parseMarkdown("```\na\n\nb\n```"))
  }

  @Test
  fun unclosedFenceRunsToEnd() {
    assertEquals(listOf(MdBlock.CodeBlock(null, "x\ny")), parseMarkdown("```\nx\ny"))
  }

  @Test
  fun indentedCode() {
    assertEquals(listOf(MdBlock.CodeBlock(null, "x")), parseMarkdown("    x"))
  }

  @Test
  fun thematicBreak() {
    assertEquals(listOf(para("a"), MdBlock.ThematicBreak, para("b")), parseMarkdown("a\n\n---\n\nb"))
  }

  @Test
  fun linkDefinitionRendersNothing() {
    assertEquals(listOf(para("a")), parseMarkdown("[r]: https://example.com\n\na"))
  }

  @Test
  fun tableFallsBackToSource() {
    val source = "|a|b|\n|-|-|\n|1|2|"
    assertEquals(listOf(para(source)), parseMarkdown(source))
  }

  @Test
  fun htmlBlockFallsBackToSource() {
    assertEquals(listOf(para("<div>x</div>")), parseMarkdown("<div>x</div>"))
  }

  @Test
  fun nestingBeyondEightFlattens() {
    val blocks = parseMarkdown("> ".repeat(10) + "deep")
    var current: MdBlock = blocks.single()
    repeat(8) {
      current = (current as MdBlock.Quote).blocks.single()
    }
    val leaf = current as MdBlock.Paragraph
    assertTrue(leaf.text.text.contains("deep"))
  }

  @Test
  fun nestedListsBeyondEightFlatten() {
    val source = (0 until 10).joinToString("\n") { "  ".repeat(it) + "- item$it" }
    var current: MdBlock = parseMarkdown(source).single()
    repeat(8) {
      current = (current as MdBlock.ListBlock).items.single().blocks.last()
    }
    assertTrue(current is MdBlock.Paragraph)
    assertTrue(current.text.text.contains("item9"))
  }

  @Test
  fun emptyAndBlank() {
    assertEquals(emptyList(), parseMarkdown(""))
    assertEquals(emptyList(), parseMarkdown("  \n\n"))
  }

  @Test
  fun crlfNormalized() {
    val blocks = parseMarkdown("a\r\nb\r\n\r\nc")
    assertEquals(listOf(para("a\nb"), para("c")), blocks)
    assertFalse(blocks.any { it is MdBlock.Paragraph && '\r' in it.text.text })
  }

  @Test
  fun failureFallsBackToPlainParagraph() {
    var reported: Throwable? = null
    val parser = MarkdownBodyParser(onFailure = { reported = it }, buildTree = { error("boom") })
    assertEquals(listOf(para("x *y*")), parser.parse("x *y*"))
    assertEquals("boom", reported?.message)
    assertTrue(reported is IllegalStateException)
  }
}

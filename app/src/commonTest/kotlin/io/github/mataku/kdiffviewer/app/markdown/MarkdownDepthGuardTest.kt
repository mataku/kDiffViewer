package io.github.mataku.kdiffviewer.app.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MarkdownDepthGuardTest {
  private fun assertPlainFallback(source: String) {
    val block = parseMarkdown(source).single()
    assertTrue(block is MdBlock.Paragraph)
    assertTrue(block.text.spans.isEmpty())
  }

  @Test
  fun hundredThousandNestedQuotesFallBackQuickly() {
    assertPlainFallback(">".repeat(100_000) + " deep")
  }

  @Test
  fun twoThousandNestedQuotesWithReferenceLinkDoNotThrow() {
    assertPlainFallback("[a][r]\n\n" + "> ".repeat(2_000) + "deep\n\n[r]: https://e.com")
  }

  @Test
  fun listMarkerChainOnOneLineFallsBack() {
    assertPlainFallback("- ".repeat(100) + "x")
  }

  @Test
  fun orderedAndMixedMarkerChainFallsBack() {
    assertPlainFallback("1. > ".repeat(50) + "x")
  }

  @Test
  fun indentDrivenListNestingFallsBack() {
    assertPlainFallback((0 until 200).joinToString("\n") { "  ".repeat(it) + "- item$it" })
  }

  @Test
  fun moderateNestingStillParses() {
    val block = parseMarkdown("> ".repeat(10) + "deep").single()
    assertTrue(block is MdBlock.Quote)
  }

  @Test
  fun longIndentedCodeLineIsNotMistakenForNesting() {
    val block = parseMarkdown("para\n\n" + " ".repeat(300) + "code").single { it is MdBlock.CodeBlock }
    assertTrue(block is MdBlock.CodeBlock)
  }

  @Test
  fun stackOverflowFromTreeBuildFallsBack() {
    var reported = 0
    val parser = MarkdownBodyParser(onFailure = { reported++ }, buildTree = { throw StackOverflowError() })
    val block = parser.parse("hello").single()
    assertTrue(block is MdBlock.Paragraph)
    assertEquals("hello", block.text.text)
    assertEquals(1, reported)
  }

  @Test
  fun otherErrorsPropagate() {
    val parser = MarkdownBodyParser(buildTree = { throw AssertionError("boom") })
    assertFailsWith<AssertionError> { parser.parse("hello") }
  }
}

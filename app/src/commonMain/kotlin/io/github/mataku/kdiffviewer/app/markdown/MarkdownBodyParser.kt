package io.github.mataku.kdiffviewer.app.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

private const val MAX_NESTING = 8

private val gfmParser = MarkdownParser(GFMFlavourDescriptor(), true, CancellationToken.NonCancellable)

private fun gfmTree(source: String): ASTNode {
  val text: CharSequence = source
  return gfmParser.buildMarkdownTreeFromString(text)
}

class MarkdownBodyParser(
  private val onFailure: (Throwable) -> Unit = { it.printStackTrace() },
  private val buildTree: (String) -> ASTNode = ::gfmTree,
) {
  fun parse(source: String): List<MdBlock> {
    val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
    if (normalized.isBlank()) return emptyList()
    return try {
      val root = buildTree(normalized)
      Mapper(normalized).blocks(root, 0)
    } catch (e: Throwable) {
      onFailure(e)
      listOf(MdBlock.Paragraph(MdInline(normalized, emptyList())))
    }
  }

  private class Mapper(
    private val source: String,
  ) {
    fun blocks(
      container: ASTNode,
      depth: Int,
    ): List<MdBlock> = container.children.mapNotNull { block(it, depth) }

    private fun block(
      node: ASTNode,
      depth: Int,
    ): MdBlock? {
      val type = node.type
      return when {
        type in IGNORED -> null
        type == MarkdownElementTypes.PARAGRAPH -> MdBlock.Paragraph(inline(node))
        type in ATX_LEVELS -> heading(ATX_LEVELS.getValue(type), node, MarkdownTokenTypes.ATX_CONTENT)
        type in SETEXT_LEVELS -> heading(SETEXT_LEVELS.getValue(type), node, MarkdownTokenTypes.SETEXT_CONTENT)
        type == MarkdownTokenTypes.HORIZONTAL_RULE -> MdBlock.ThematicBreak
        type == MarkdownElementTypes.CODE_FENCE -> fencedCode(node)
        type == MarkdownElementTypes.CODE_BLOCK -> indentedCode(node)
        depth >= MAX_NESTING && type in NESTING -> sourceParagraph(node)
        type == MarkdownElementTypes.BLOCK_QUOTE -> MdBlock.Quote(blocks(node, depth + 1))
        type == MarkdownElementTypes.UNORDERED_LIST -> list(node, ordered = false, depth)
        type == MarkdownElementTypes.ORDERED_LIST -> list(node, ordered = true, depth)
        else -> sourceParagraph(node)
      }
    }

    private fun text(node: ASTNode): String = node.getTextInNode(source).toString()

    private fun sourceParagraph(node: ASTNode): MdBlock? {
      val raw = text(node)
      return if (raw.isBlank()) null else MdBlock.Paragraph(MdInline(raw, emptyList()))
    }

    private fun heading(
      level: Int,
      node: ASTNode,
      contentType: IElementType,
    ): MdBlock {
      val content = node.children.firstOrNull { it.type == contentType }
      return MdBlock.Heading(level, if (content == null) MdInline("", emptyList()) else inline(content))
    }

    private fun inline(node: ASTNode): MdInline {
      val flat = StringBuilder()
      collectLeaves(node, flat)
      val text = flat.lines().joinToString("\n") { it.trimStart() }.trim()
      return MdInline(text, emptyList())
    }

    private fun collectLeaves(
      node: ASTNode,
      out: StringBuilder,
    ) {
      if (node.children.isEmpty()) {
        if (node.type != MarkdownTokenTypes.BLOCK_QUOTE) out.append(text(node))
      } else {
        node.children.forEach { collectLeaves(it, out) }
      }
    }

    private fun fencedCode(node: ASTNode): MdBlock {
      val info = node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }?.let { text(it) }?.takeIf { it.isNotBlank() }
      val code = StringBuilder()
      var pendingBreaks = 0
      var started = false
      for (child in node.children) {
        when (child.type) {
          MarkdownTokenTypes.EOL -> if (started) pendingBreaks++ else started = true

          MarkdownTokenTypes.CODE_FENCE_CONTENT -> {
            repeat(pendingBreaks) { code.append('\n') }
            pendingBreaks = 0
            code.append(text(child))
          }
        }
      }
      return MdBlock.CodeBlock(info, code.toString())
    }

    private fun indentedCode(node: ASTNode): MdBlock {
      val code =
        node.children
          .filter { it.type == MarkdownTokenTypes.CODE_LINE || it.type == MarkdownTokenTypes.EOL }
          .joinToString("") { child ->
            val raw = text(child)
            if (child.type == MarkdownTokenTypes.EOL) raw else raw.removePrefix("    ").removePrefix("\t")
          }
      return MdBlock.CodeBlock(null, code)
    }

    private fun list(
      node: ASTNode,
      ordered: Boolean,
      depth: Int,
    ): MdBlock {
      val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
      val start =
        if (ordered) {
          items
            .firstOrNull()
            ?.children
            ?.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
            ?.let { text(it).takeWhile(Char::isDigit).toIntOrNull() }
            ?: 1
        } else {
          1
        }
      return MdBlock.ListBlock(ordered, start, items.map { listItem(it, depth + 1) })
    }

    private fun listItem(
      node: ASTNode,
      depth: Int,
    ): ListItem {
      val checkBox = node.children.firstOrNull { it.type == GFMTokenTypes.CHECK_BOX }
      val checked = checkBox?.let { !text(it).contains("[ ]") }
      return ListItem(checked, blocks(node, depth))
    }
  }

  private companion object {
    val IGNORED =
      setOf<IElementType>(
        MarkdownTokenTypes.EOL,
        MarkdownTokenTypes.WHITE_SPACE,
        MarkdownTokenTypes.BLOCK_QUOTE,
        MarkdownTokenTypes.LIST_BULLET,
        MarkdownTokenTypes.LIST_NUMBER,
        GFMTokenTypes.CHECK_BOX,
        MarkdownElementTypes.LINK_DEFINITION,
      )

    val NESTING = setOf<IElementType>(MarkdownElementTypes.BLOCK_QUOTE, MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST)

    val ATX_LEVELS =
      mapOf<IElementType, Int>(
        MarkdownElementTypes.ATX_1 to 1,
        MarkdownElementTypes.ATX_2 to 2,
        MarkdownElementTypes.ATX_3 to 3,
        MarkdownElementTypes.ATX_4 to 4,
        MarkdownElementTypes.ATX_5 to 5,
        MarkdownElementTypes.ATX_6 to 6,
      )

    val SETEXT_LEVELS =
      mapOf<IElementType, Int>(
        MarkdownElementTypes.SETEXT_1 to 1,
        MarkdownElementTypes.SETEXT_2 to 2,
      )
  }
}

private val defaultParser = MarkdownBodyParser()

fun parseMarkdown(source: String): List<MdBlock> = defaultParser.parse(source)

package io.github.mataku.kdiffviewer.app.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.entities.Entities
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import kotlin.coroutines.cancellation.CancellationException

private const val MAX_NESTING = 8
private const val MAX_INLINE_DEPTH = 32
private const val MAX_SOURCE_CHARS = 65_536

private val gfmParser = MarkdownParser(GFMFlavourDescriptor(), true, CancellationToken.NonCancellable)

private fun gfmTree(source: String): ASTNode {
  val text: CharSequence = source
  return gfmParser.buildMarkdownTreeFromString(text)
}

private val textEscapes = Regex("""\\([!-/:-@\[-`{-~])|&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});""")

private fun decodeText(raw: String): String {
  if ('\\' !in raw && '&' !in raw) return raw
  return textEscapes.replace(raw) { match ->
    val escaped = match.groupValues[1]
    if (escaped.isNotEmpty()) return@replace escaped
    val entity = match.groupValues[2]
    val codePoint =
      when {
        entity.startsWith("#x") || entity.startsWith("#X") -> entity.substring(2).toIntOrNull(16)
        entity.startsWith("#") -> entity.substring(1).toIntOrNull()
        else -> Entities.map["&$entity;"] ?: Entities.map[entity]
      }
    if (codePoint == null || codePoint <= 0 || codePoint > 0x10FFFF || codePoint in 0xD800..0xDFFF) {
      match.value
    } else {
      codePointToString(codePoint)
    }
  }
}

private fun codePointToString(codePoint: Int): String = if (codePoint < 0x10000) {
  codePoint.toChar().toString()
} else {
  val offset = codePoint - 0x10000
  charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
}

private fun isAllowedLinkUrl(url: String): Boolean {
  if (url.any { it.isWhitespace() || it.isISOControl() }) return false
  val colon = url.indexOf(':')
  if (colon <= 0) return false
  val scheme = url.substring(0, colon).lowercase()
  return scheme == "http" || scheme == "https" || scheme == "mailto"
}

class MarkdownBodyParser(
  private val onFailure: (Throwable) -> Unit = { it.printStackTrace() },
  private val buildTree: (String) -> ASTNode = ::gfmTree,
) {
  fun parse(source: String): List<MdBlock> {
    val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
    if (normalized.isBlank()) return emptyList()
    if (normalized.length > MAX_SOURCE_CHARS) return plainFallback(normalized)
    if (exceedsContainerDepth(normalized)) return plainFallback(normalized)
    return try {
      val root = buildTree(normalized)
      Mapper(root, normalized).blocks(root, 0)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      onFailure(e)
      plainFallback(normalized)
    } catch (e: Throwable) {
      if (!isStackOverflow(e)) throw e
      onFailure(e)
      plainFallback(normalized)
    }
  }

  private fun plainFallback(text: String) = listOf<MdBlock>(MdBlock.Paragraph(MdInline(text, emptyList())))

  private class Mapper(
    private val root: ASTNode,
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

    private val linkMap by lazy(LazyThreadSafetyMode.NONE) { LinkMap.buildLinkMap(root, source) }

    private fun inline(node: ASTNode): MdInline {
      val writer = InlineWriter()
      writer.children(node.children)
      return writer.finish()
    }

    private inner class InlineWriter {
      private val out = StringBuilder()
      private val spans = mutableListOf<MdSpan>()

      fun finish(): MdInline {
        trimTrailingBlanks()
        return MdInline(out.toString(), spans)
      }

      private var depth = 0

      fun children(nodes: List<ASTNode>) {
        if (depth >= MAX_INLINE_DEPTH) {
          nodes.forEach { out.append(text(it)) }
          return
        }
        depth++
        try {
          nodes.forEach { visit(it) }
        } finally {
          depth--
        }
      }

      private fun atLineStart() = out.isEmpty() || out.last() == '\n'

      private fun trimTrailingBlanks() {
        var end = out.length
        while (end > 0 && (out[end - 1] == ' ' || out[end - 1] == '\t')) end--
        out.setLength(end)
        spans.replaceAll { if (it.end > end) it.copy(end = end) else it }
        spans.removeAll { it.start >= it.end }
      }

      private fun span(
        kind: MdSpanKind,
        start: Int,
        url: String? = null,
      ) {
        if (out.length > start) spans.add(MdSpan(kind, start, out.length, url))
      }

      private fun visit(node: ASTNode) {
        val type = node.type
        when (type) {
          MarkdownTokenTypes.EOL -> {
            trimTrailingBlanks()
            out.append('\n')
          }

          MarkdownTokenTypes.HARD_LINE_BREAK, MarkdownTokenTypes.BLOCK_QUOTE -> {}

          MarkdownTokenTypes.WHITE_SPACE -> if (!atLineStart()) out.append(text(node))

          MarkdownTokenTypes.TEXT -> out.append(decodeText(text(node)))

          MarkdownElementTypes.STRONG -> styled(MdSpanKind.Strong, node, MarkdownTokenTypes.EMPH)

          MarkdownElementTypes.EMPH -> styled(MdSpanKind.Emphasis, node, MarkdownTokenTypes.EMPH)

          GFMElementTypes.STRIKETHROUGH -> styled(MdSpanKind.Strikethrough, node, GFMTokenTypes.TILDE)

          MarkdownElementTypes.CODE_SPAN -> codeSpan(node)

          MarkdownElementTypes.INLINE_LINK -> inlineLink(node)

          MarkdownElementTypes.FULL_REFERENCE_LINK -> referenceLink(node, node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }, node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL })

          MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
            val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
            referenceLink(node, label, label)
          }

          MarkdownElementTypes.AUTOLINK -> autolink(node)

          GFMTokenTypes.GFM_AUTOLINK -> link(text(node)) { out.append(text(node)) }

          MarkdownElementTypes.IMAGE -> children(node.children.filter { it.type != MarkdownTokenTypes.EXCLAMATION_MARK })

          else -> if (node.children.isEmpty()) out.append(text(node)) else children(node.children)
        }
      }

      private fun inner(
        node: ASTNode,
        marker: IElementType,
      ): List<ASTNode> {
        val kids = node.children
        val from = kids.indexOfFirst { it.type != marker }
        if (from < 0) return emptyList()
        val to = kids.indexOfLast { it.type != marker }
        return kids.subList(from, to + 1)
      }

      private fun styled(
        kind: MdSpanKind,
        node: ASTNode,
        marker: IElementType,
      ) {
        val start = out.length
        children(inner(node, marker))
        span(kind, start)
      }

      private fun codeSpan(node: ASTNode) {
        val start = out.length
        inner(node, MarkdownTokenTypes.BACKTICK).forEach { out.append(text(it)) }
        span(MdSpanKind.Code, start)
      }

      private fun link(
        url: String,
        content: () -> Unit,
      ) {
        val start = out.length
        content()
        if (isAllowedLinkUrl(url)) span(MdSpanKind.Link, start, url)
      }

      private fun bracketed(node: ASTNode): List<ASTNode> {
        val kids = node.children
        val from = if (kids.firstOrNull()?.type == MarkdownTokenTypes.LBRACKET) 1 else 0
        val to = if (kids.lastOrNull()?.type == MarkdownTokenTypes.RBRACKET) kids.size - 1 else kids.size
        return kids.subList(from, maxOf(from, to))
      }

      private fun inlineLink(node: ASTNode) {
        val linkText = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
        val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
        val url = destination?.let { text(it).removeSurrounding("<", ">") }
        if (linkText == null || url == null) {
          children(node.children)
        } else {
          link(url) { children(bracketed(linkText)) }
        }
      }

      private fun referenceLink(
        node: ASTNode,
        content: ASTNode?,
        label: ASTNode?,
      ) {
        val labelText = label?.let { text(it) }?.takeUnless { it == "[]" } ?: content?.let { text(it) }
        val url = labelText?.let { linkMap.getLinkInfo(it)?.destination?.toString() }
        if (content == null || url == null) {
          children(node.children)
        } else {
          link(url) { children(bracketed(content)) }
        }
      }

      private fun autolink(node: ASTNode) {
        val target = node.children.firstOrNull { it.type == MarkdownTokenTypes.AUTOLINK }
        if (target == null) {
          children(node.children)
          return
        }
        val raw = text(target)
        val url = if (':' !in raw && '@' in raw) "mailto:$raw" else raw
        link(url) { out.append(raw) }
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
            if (child.type == MarkdownTokenTypes.EOL) {
              raw
            } else if (raw.startsWith("    ")) {
              raw.substring(4)
            } else {
              raw.removePrefix("\t")
            }
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

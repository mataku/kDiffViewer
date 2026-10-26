package io.github.mataku.kdiffviewer.app.markdown

sealed interface MdBlock {
  data class Paragraph(
    val text: MdInline,
  ) : MdBlock

  data class Heading(
    val level: Int,
    val text: MdInline,
  ) : MdBlock

  data class ListBlock(
    val ordered: Boolean,
    val start: Int,
    val items: List<ListItem>,
  ) : MdBlock

  data class Quote(
    val blocks: List<MdBlock>,
  ) : MdBlock

  data class CodeBlock(
    val info: String?,
    val code: String,
  ) : MdBlock

  data object ThematicBreak : MdBlock
}

data class ListItem(
  val checked: Boolean?,
  val blocks: List<MdBlock>,
)

data class MdInline(
  val text: String,
  val spans: List<MdSpan>,
)

data class MdSpan(
  val kind: MdSpanKind,
  val start: Int,
  val end: Int,
  val url: String? = null,
)

enum class MdSpanKind { Strong, Emphasis, Strikethrough, Code, Link }

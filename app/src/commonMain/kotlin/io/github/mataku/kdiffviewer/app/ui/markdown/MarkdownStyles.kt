package io.github.mataku.kdiffviewer.app.ui.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.markdown.MdSpanKind
import io.github.mataku.kdiffviewer.app.ui.DefaultDiffTypography
import io.github.mataku.kdiffviewer.app.ui.DiffColors
import io.github.mataku.kdiffviewer.app.ui.DiffTypography
import io.github.mataku.kdiffviewer.app.ui.LightDiffColors

@Immutable
class MarkdownStyles(colors: DiffColors, typography: DiffTypography) {
  val body = TextStyle(color = colors.text, fontSize = 13.sp)
  val quote = body.copy(color = colors.mutedText)
  val code: TextStyle = typography.code
  val codeBackground: Color = colors.text.copy(alpha = 0.06f)
  val quoteBar: Color = colors.mutedText
  val rule: Color = colors.threadBorder
  val mutedText: Color = colors.mutedText

  private val headings = arrayOf(17, 15, 14, 13, 13, 13).map {
    body.copy(fontSize = it.sp, fontWeight = FontWeight.SemiBold)
  }

  private val spanStyles = MdSpanKind.entries.map { kind ->
    when (kind) {
      MdSpanKind.Strong -> SpanStyle(fontWeight = FontWeight.Bold)
      MdSpanKind.Emphasis -> SpanStyle(fontStyle = FontStyle.Italic)
      MdSpanKind.Strikethrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
      MdSpanKind.Code -> SpanStyle(fontFamily = typography.code.fontFamily, background = codeBackground)
      MdSpanKind.Link -> SpanStyle(color = colors.accent)
    }
  }

  val linkStyles = TextLinkStyles(
    style = SpanStyle(color = colors.accent),
    hoveredStyle = SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline),
  )

  fun heading(level: Int): TextStyle = headings[(level - 1).coerceIn(0, headings.lastIndex)]

  fun spanStyle(kind: MdSpanKind): SpanStyle = spanStyles[kind.ordinal]
}

val LocalMarkdownStyles = staticCompositionLocalOf { MarkdownStyles(LightDiffColors, DefaultDiffTypography) }

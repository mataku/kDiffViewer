package io.github.mataku.kdiffviewer.app.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.markdown.ListItem
import io.github.mataku.kdiffviewer.app.markdown.MdBlock
import io.github.mataku.kdiffviewer.app.markdown.MdInline
import io.github.mataku.kdiffviewer.app.markdown.MdSpanKind
import io.github.mataku.kdiffviewer.app.markdown.parseMarkdown

private val BlockSpacing = 8.dp
private val ListIndent = 16.dp
private val MarkerWidth = 20.dp

@Composable
fun MarkdownBody(source: String, modifier: Modifier = Modifier) {
  val styles = LocalMarkdownStyles.current
  val blocks = remember(source) { parseMarkdown(source) }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(BlockSpacing)) {
    blocks.forEach { Block(it, styles.body, styles) }
  }
}

@Composable
private fun Block(block: MdBlock, textStyle: TextStyle, styles: MarkdownStyles) {
  when (block) {
    is MdBlock.Paragraph -> InlineText(block.text, textStyle, styles)

    is MdBlock.Heading -> InlineText(block.text, styles.heading(block.level), styles)

    is MdBlock.ListBlock -> ListView(block, textStyle, styles)

    is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
      Box(Modifier.width(3.dp).fillMaxHeight().background(styles.quoteBar))
      Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(BlockSpacing)) {
        block.blocks.forEach { Block(it, styles.quote, styles) }
      }
    }

    is MdBlock.CodeBlock -> CodeBlockView(block.info, block.code)

    MdBlock.ThematicBreak -> Box(Modifier.fillMaxWidth().height(0.5.dp).background(styles.rule))
  }
}

@Composable
private fun ListView(list: MdBlock.ListBlock, textStyle: TextStyle, styles: MarkdownStyles) {
  Column(Modifier.padding(start = ListIndent), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    list.items.forEachIndexed { index, item -> ListItemView(item, marker(list, index, item), textStyle, styles) }
  }
}

private fun marker(list: MdBlock.ListBlock, index: Int, item: ListItem): String = when {
  item.checked != null -> if (item.checked) "☑" else "☐"
  list.ordered -> "${list.start + index}."
  else -> "•"
}

@Composable
private fun ListItemView(item: ListItem, marker: String, textStyle: TextStyle, styles: MarkdownStyles) {
  Row {
    BasicText(marker, style = textStyle, modifier = Modifier.width(MarkerWidth))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      item.blocks.forEach { Block(it, textStyle, styles) }
    }
  }
}

@Composable
private fun InlineText(inline: MdInline, style: TextStyle, styles: MarkdownStyles) {
  val text = remember(inline, styles) { inline.toAnnotatedString(styles) }
  BasicText(text, style = style)
}

private fun MdInline.toAnnotatedString(styles: MarkdownStyles): AnnotatedString = buildAnnotatedString {
  append(text)
  spans.forEach { span ->
    if (span.kind == MdSpanKind.Link && span.url != null) {
      addLink(LinkAnnotation.Url(span.url, styles.linkStyles), span.start, span.end)
    } else {
      addStyle(styles.spanStyle(span.kind), span.start, span.end)
    }
  }
}

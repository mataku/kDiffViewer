package io.github.mataku.kdiffviewer.app.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.mataku.compose.highlight.core.LocalSyntaxTheme
import io.github.mataku.kdiffviewer.app.highlight.LocalCodeHighlighter

private val CodeShape = RoundedCornerShape(6.dp)

@Composable
fun CodeBlockView(info: String?, code: String, modifier: Modifier = Modifier) {
  val styles = LocalMarkdownStyles.current
  val highlighter = LocalCodeHighlighter.current
  val theme = LocalSyntaxTheme.current
  var highlighted by remember(code, info, theme, highlighter) { mutableStateOf<AnnotatedString?>(null) }
  LaunchedEffect(code, info, theme, highlighter) {
    highlighted = highlighter?.highlight(code, info, theme)
  }
  val plain = remember(code) { AnnotatedString(code) }
  Box(
    modifier
      .fillMaxWidth()
      .clip(CodeShape)
      .background(styles.codeBackground)
      .horizontalScroll(rememberScrollState())
      .padding(8.dp),
  ) {
    BasicText(text = highlighted ?: plain, style = styles.code, softWrap = false)
  }
}

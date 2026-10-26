package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

internal const val SYSTEM_MONOSPACE = "System monospace"

@Composable
fun FontFamilyPicker(
  selected: String?,
  fontFamilies: List<String>?,
  onSelect: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  var open by remember { mutableStateOf(false) }
  val textStyle = TextStyle(color = colors.text, fontSize = 13.sp)
  Box(modifier = modifier) {
    BasicText(
      text = "${selected ?: SYSTEM_MONOSPACE}  ▾",
      style = textStyle,
      maxLines = 1,
      modifier = Modifier
        .testTag("font-picker")
        .width(240.dp)
        .border(1.dp, colors.border)
        .background(colors.fileHeaderBackground)
        .clickable { open = true }
        .padding(horizontal = 10.dp, vertical = 6.dp),
    )
    if (open) {
      Popup(
        offset = IntOffset(0, 0),
        onDismissRequest = { open = false },
        properties = PopupProperties(focusable = true),
      ) {
        FontPopupContent(
          fontFamilies = fontFamilies,
          textStyle = textStyle,
          onSelect = {
            open = false
            onSelect(it)
          },
        )
      }
    }
  }
}

@Composable
private fun FontPopupContent(
  fontFamilies: List<String>?,
  textStyle: TextStyle,
  onSelect: (String?) -> Unit,
) {
  val colors = LocalDiffColors.current
  var query by remember { mutableStateOf("") }
  val filtered = remember(query, fontFamilies) {
    val all = fontFamilies.orEmpty()
    if (query.isBlank()) all else all.filter { it.contains(query.trim(), ignoreCase = true) }
  }
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  Column(
    modifier = Modifier
      .width(240.dp)
      .border(1.dp, colors.border)
      .background(colors.background),
  ) {
    BasicTextField(
      value = query,
      onValueChange = { query = it },
      singleLine = true,
      textStyle = textStyle,
      cursorBrush = SolidColor(colors.text),
      modifier = Modifier
        .testTag("font-filter")
        .fillMaxWidth()
        .background(colors.fileHeaderBackground)
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .focusRequester(focusRequester),
    )
    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
      item(key = "system-monospace") {
        FontItem(SYSTEM_MONOSPACE, textStyle) { onSelect(null) }
      }
      items(filtered, key = { it }) { name ->
        FontItem(name, textStyle) { onSelect(name) }
      }
    }
  }
}

@Composable
private fun FontItem(name: String, textStyle: TextStyle, onClick: () -> Unit) {
  BasicText(
    text = name,
    style = textStyle,
    maxLines = 1,
    modifier = Modifier
      .testTag("font-item-$name")
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 5.dp),
  )
}

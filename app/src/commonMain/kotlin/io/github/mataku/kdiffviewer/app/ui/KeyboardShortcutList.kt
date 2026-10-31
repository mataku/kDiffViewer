package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KeyboardShortcutList(groups: List<ShortcutGroup>, modifier: Modifier = Modifier) {
  val colors = LocalDiffColors.current
  val styles = remember(colors) { ShortcutListStyles(colors) }
  Column(modifier = modifier.testTag("keyboard-shortcuts"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    groups.forEach { group ->
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(text = group.title, style = styles.group)
        group.entries.forEach { entry -> ShortcutRow(entry, styles) }
      }
    }
  }
}

@Composable
private fun ShortcutRow(entry: ShortcutEntry, styles: ShortcutListStyles) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicText(text = entry.action, style = styles.action, modifier = Modifier.weight(1f))
    BasicText(text = entry.chords.joinToString("  ") { it.label }, style = styles.keys)
  }
}

private class ShortcutListStyles(colors: DiffColors) {
  val group = TextStyle(color = colors.mutedText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
  val action = TextStyle(color = colors.text, fontSize = 13.sp)
  val keys = TextStyle(color = colors.mutedText, fontSize = 13.sp)
}

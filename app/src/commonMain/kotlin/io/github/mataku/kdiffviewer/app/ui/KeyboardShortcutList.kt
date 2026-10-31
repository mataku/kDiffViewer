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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ui.mac.MacGroupedList
import io.github.mataku.kdiffviewer.app.ui.mac.MacInlineError
import io.github.mataku.kdiffviewer.app.ui.mac.MacPushButton
import io.github.mataku.kdiffviewer.app.ui.mac.MacShortcutRecorder

private val ShortcutsByCategory: List<Pair<ShortcutCategory, List<ShortcutId>>> =
  ShortcutCategory.entries.map { category -> category to ShortcutId.entries.filter { it.category == category } }

@Composable
fun KeyboardShortcutList(
  resolved: ResolvedShortcuts,
  state: KeyboardShortcutsState,
  onCommit: (ShortcutId, Chord) -> Unit,
  onRestoreDefaults: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalDiffColors.current
  val styles = remember(colors) { ShortcutListStyles(colors) }
  Column(modifier = modifier.testTag("keyboard-shortcuts"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    ShortcutsByCategory.forEach { (category, ids) ->
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(text = category.title, style = styles.group)
        MacGroupedList(ids) { id ->
          ShortcutRow(
            id = id,
            chord = resolved.shortcuts[id],
            problem = problemLine(id, resolved, state),
            recording = state.recording == id,
            styles = styles,
            onStartRecording = { state.startRecording(id) },
            onKey = { event -> state.onKey(event, resolved.shortcuts, onCommit) },
            onCancel = state::cancel,
          )
        }
      }
    }
    MacPushButton("Restore Defaults", onRestoreDefaults, Modifier.testTag("shortcuts-restore-defaults"))
  }
}

private fun problemLine(id: ShortcutId, resolved: ResolvedShortcuts, state: KeyboardShortcutsState): String? {
  val keymap = resolved.shortcuts.keymap
  if (state.recording == id) return state.rejection?.message(keymap)
  return resolved.problems[id]?.let { "settings.json: ${it.message(keymap)}" }
}

@Composable
private fun ShortcutRow(
  id: ShortcutId,
  chord: Chord,
  problem: String?,
  recording: Boolean,
  styles: ShortcutListStyles,
  onStartRecording: () -> Unit,
  onKey: (KeyEvent) -> Boolean,
  onCancel: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      BasicText(text = id.title, style = styles.action)
      if (problem != null) MacInlineError(problem, Modifier.testTag("shortcut-problem-${id.settingsKey}"))
    }
    MacShortcutRecorder(
      label = chord.label,
      recording = recording,
      onStartRecording = onStartRecording,
      onKey = onKey,
      onCancel = onCancel,
      modifier = Modifier.testTag("shortcut-${id.settingsKey}"),
    )
  }
}

private class ShortcutListStyles(colors: DiffColors) {
  val group = TextStyle(color = colors.mutedText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
  val action = TextStyle(color = colors.text, fontSize = 13.sp)
}

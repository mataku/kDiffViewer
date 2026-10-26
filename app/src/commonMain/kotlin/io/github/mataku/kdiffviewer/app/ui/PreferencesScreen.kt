package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.settings.Appearance
import io.github.mataku.kdiffviewer.app.settings.GitStatus
import io.github.mataku.kdiffviewer.app.settings.Settings

private const val NOT_USABLE_MESSAGE = "Not a usable git executable"
private const val NOT_FOUND_MESSAGE = "git was not found"
private const val MIN_FONT_SIZE = 8
private const val MAX_FONT_SIZE = 32
private val TAB_WIDTHS = listOf(2, 4, 8)

@Composable
fun PreferencesScreen(
  settings: Settings,
  loadError: Boolean,
  gitStatus: GitStatus,
  fontFamilies: List<String>?,
  onUpdate: ((Settings) -> Settings) -> Unit,
  onCommitGitPath: (String) -> Unit,
  onChooseGit: () -> Unit,
) {
  val colors = LocalDiffColors.current
  Column(
    modifier = Modifier.fillMaxSize().background(colors.background).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (loadError) {
      BasicText(
        text = "Could not read settings file; using defaults",
        style = TextStyle(color = colors.removedText, fontSize = 13.sp),
        modifier = Modifier
          .testTag("settings-load-error")
          .fillMaxWidth()
          .background(colors.removedBackground)
          .padding(10.dp),
      )
    }
    SettingRow("Appearance") {
      Segmented(
        options = Appearance.entries,
        selected = settings.appearance,
        label = { it.name },
        tag = { "appearance-${it.name}" },
        onSelect = { value -> onUpdate { it.copy(appearance = value) } },
      )
    }
    SettingRow("Font") {
      FontFamilyPicker(
        selected = settings.fontFamily,
        fontFamilies = fontFamilies,
        onSelect = { value -> onUpdate { it.copy(fontFamily = value) } },
      )
    }
    SettingRow("Font size") {
      FontSizeStepper(
        size = settings.fontSize,
        onChange = { delta -> onUpdate { it.copy(fontSize = (it.fontSize + delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)) } },
      )
    }
    SettingRow("Tab width") {
      Segmented(
        options = TAB_WIDTHS,
        selected = settings.tabWidth,
        label = { it.toString() },
        tag = { "tab-width-$it" },
        onSelect = { value -> onUpdate { it.copy(tabWidth = value) } },
      )
    }
    SettingRow("Git", alignTop = true) {
      GitSection(settings.gitPath, gitStatus, onCommitGitPath, onChooseGit)
    }
  }
}

@Composable
private fun SettingRow(label: String, alignTop: Boolean = false, content: @Composable () -> Unit) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
  ) {
    BasicText(
      text = label,
      style = TextStyle(color = LocalDiffColors.current.text, fontSize = 13.sp),
      modifier = Modifier.width(88.dp).padding(top = if (alignTop) 6.dp else 0.dp),
    )
    content()
  }
}

@Composable
private fun <T> Segmented(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  tag: (T) -> String,
  onSelect: (T) -> Unit,
) {
  val colors = LocalDiffColors.current
  Row(modifier = Modifier.border(1.dp, colors.border)) {
    options.forEach { option ->
      val isSelected = option == selected
      BasicText(
        text = label(option),
        style = TextStyle(color = colors.text, fontSize = 13.sp),
        modifier = Modifier
          .testTag(tag(option))
          .background(if (isSelected) colors.hunkBackground else colors.fileHeaderBackground)
          .semantics { this.selected = isSelected }
          .clickable(role = Role.RadioButton) { onSelect(option) }
          .padding(horizontal = 14.dp, vertical = 6.dp),
      )
    }
  }
}

@Composable
private fun FontSizeStepper(size: Int, onChange: (delta: Int) -> Unit) {
  val colors = LocalDiffColors.current
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    StepButton("−", "font-size-decrease", size > MIN_FONT_SIZE) { onChange(-1) }
    BasicText(
      text = size.toString(),
      style = TextStyle(color = colors.text, fontSize = 13.sp),
      modifier = Modifier.width(24.dp),
    )
    StepButton("+", "font-size-increase", size < MAX_FONT_SIZE) { onChange(1) }
    BasicText(
      text = "($MIN_FONT_SIZE–$MAX_FONT_SIZE)",
      style = TextStyle(color = colors.mutedText, fontSize = 12.sp),
    )
  }
}

@Composable
private fun StepButton(label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
  val colors = LocalDiffColors.current
  BasicText(
    text = label,
    style = TextStyle(color = if (enabled) colors.text else colors.mutedText.copy(alpha = 0.5f), fontSize = 14.sp),
    modifier = Modifier
      .testTag(tag)
      .border(1.dp, colors.border)
      .background(colors.fileHeaderBackground)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 4.dp),
  )
}

@Composable
private fun GitSection(
  gitPath: String?,
  status: GitStatus,
  onCommit: (String) -> Unit,
  onChoose: () -> Unit,
) {
  val colors = LocalDiffColors.current
  var text by remember(gitPath) { mutableStateOf(gitPath.orEmpty()) }
  var hadFocus by remember { mutableStateOf(false) }
  val latestText by rememberUpdatedState(text)
  val latestGitPath by rememberUpdatedState(gitPath)
  val latestOnCommit by rememberUpdatedState(onCommit)
  DisposableEffect(Unit) {
    onDispose {
      if (latestText != latestGitPath.orEmpty()) latestOnCommit(latestText)
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(
        modifier = Modifier
          .width(300.dp)
          .border(1.dp, colors.border)
          .background(colors.fileHeaderBackground)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        BasicTextField(
          value = text,
          onValueChange = { text = it },
          singleLine = true,
          textStyle = TextStyle(color = colors.text, fontSize = 13.sp),
          cursorBrush = SolidColor(colors.text),
          modifier = Modifier
            .testTag("git-path-field")
            .fillMaxWidth()
            .semantics { status.errorMessage()?.let { error(it) } }
            .onFocusChanged { state ->
              if (hadFocus && !state.isFocused) onCommit(text)
              hadFocus = state.isFocused
            }
            .onPreviewKeyEvent { event ->
              if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                onCommit(text)
                true
              } else {
                false
              }
            },
        )
      }
      BasicText(
        text = "Choose…",
        style = TextStyle(color = colors.text, fontSize = 13.sp),
        modifier = Modifier
          .testTag("git-choose")
          .border(1.dp, colors.border)
          .background(colors.fileHeaderBackground)
          .clickable(role = Role.Button, onClick = onChoose)
          .padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
    val line = statusLine(gitPath, status, colors)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.testTag("git-status").semantics(mergeDescendants = true) {},
    ) {
      when (line.icon) {
        StatusIcon.Ok -> StatusImage(KdvIcons.CheckCircle, "git-status-icon-ok", line.color)
        StatusIcon.Error -> StatusImage(KdvIcons.XCircle, "git-status-icon-error", line.color)
        null -> Unit
      }
      BasicText(text = line.message, style = TextStyle(color = line.color, fontSize = 12.sp))
    }
  }
}

@Composable
private fun StatusImage(icon: ImageVector, tag: String, color: Color) {
  Image(
    imageVector = icon,
    contentDescription = null,
    modifier = Modifier.testTag(tag).size(12.dp),
    colorFilter = ColorFilter.tint(color),
  )
}

private enum class StatusIcon { Ok, Error }

private data class StatusLine(val icon: StatusIcon?, val message: String, val color: Color)

private fun statusLine(gitPath: String?, status: GitStatus, colors: DiffColors): StatusLine = when (status) {
  GitStatus.Checking -> StatusLine(null, "", colors.mutedText)

  is GitStatus.Ready ->
    if (gitPath == null) {
      StatusLine(null, "Auto-detected: ${status.info.path}", colors.mutedText)
    } else {
      StatusLine(StatusIcon.Ok, status.info.version, colors.addedText)
    }

  is GitStatus.Rejected -> StatusLine(StatusIcon.Error, NOT_USABLE_MESSAGE, colors.removedText)

  is GitStatus.AutoDetectFailed, GitStatus.NotFound -> StatusLine(StatusIcon.Error, NOT_FOUND_MESSAGE, colors.removedText)
}

private fun GitStatus.errorMessage(): String? = when (this) {
  is GitStatus.Rejected -> NOT_USABLE_MESSAGE
  is GitStatus.AutoDetectFailed, GitStatus.NotFound -> NOT_FOUND_MESSAGE
  else -> null
}

package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.settings.Appearance
import io.github.mataku.kdiffviewer.app.settings.GitStatus
import io.github.mataku.kdiffviewer.app.settings.Settings
import io.github.mataku.kdiffviewer.app.settings.SettingsLoad
import io.github.mataku.kdiffviewer.app.ui.mac.MacPushButton
import io.github.mataku.kdiffviewer.app.ui.mac.MacSegmented
import io.github.mataku.kdiffviewer.app.ui.mac.MacSlider
import io.github.mataku.kdiffviewer.app.ui.mac.MacSwitch
import io.github.mataku.kdiffviewer.app.ui.mac.MacTextField

private const val NOT_USABLE_MESSAGE = "Not a usable git executable"
private const val NOT_FOUND_MESSAGE = "git was not found"
private val FontSizeRange = 8..32
private val TreeFontSizeRange = 10..20
private const val PARTIAL_MESSAGE = "Some settings in settings.json were invalid and were reset to defaults."
private const val UNREADABLE_MESSAGE =
  "Could not read settings.json. Using defaults; changes made here are not saved until the file is fixed."
private val TAB_WIDTHS = listOf(2, 4, 8)

@Composable
fun PreferencesScreen(
  settings: Settings,
  load: SettingsLoad,
  settingsFilePath: String,
  onRevealSettingsFile: () -> Unit,
  gitStatus: GitStatus,
  fontFamilies: List<String>?,
  onUpdate: ((Settings) -> Settings) -> Unit,
  onCommitGitPath: (String) -> Unit,
  onChooseGit: () -> Unit,
  titleBarInset: Dp = 0.dp,
  keymap: Keymap = HostKeymap,
) {
  val resolvedShortcuts = remember(settings.shortcuts, keymap) { settings.resolveShortcuts(keymap) }
  val shortcutsState = rememberKeyboardShortcutsState(keymap)
  val colors = LocalDiffColors.current
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background)
      .padding(top = titleBarInset)
      .testTag("settings-content")
      .verticalScroll(rememberScrollState())
      .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(28.dp),
  ) {
    if (load != SettingsLoad.Ok) {
      LoadBanner(load, onRevealSettingsFile)
    }
    BasicText(
      text = "Settings",
      style = TextStyle(color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold),
    )
    Section("Appearance") {
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Appearance.entries.forEach { appearance ->
          AppearanceCard(
            appearance = appearance,
            selected = appearance == settings.appearance,
            onClick = { onUpdate { it.copy(appearance = appearance) } },
          )
        }
      }
    }
    Section("Diff") {
      SettingRow("Font", "Font used for diff lines") {
        FontFamilyPicker(
          selected = settings.fontFamily,
          fontFamilies = fontFamilies,
          onSelect = { value -> onUpdate { it.copy(fontFamily = value) } },
        )
      }
      SettingRow("Font size") {
        FontSizeSlider(
          size = settings.fontSize,
          range = FontSizeRange,
          tagPrefix = "font-size",
          onChange = { value -> onUpdate { it.copy(fontSize = value) } },
        )
      }
      SettingRow("Tab width", "Columns a tab character occupies") {
        MacSegmented(
          options = TAB_WIDTHS,
          selected = settings.tabWidth,
          label = { it.toString() },
          tag = { "tab-width-$it" },
          onSelect = { value -> onUpdate { it.copy(tabWidth = value) } },
        )
      }
    }
    Section("Selection") {
      SettingRow("Copy on select", "Copy selected text to the clipboard automatically") {
        MacSwitch(
          checked = settings.copyOnSelect,
          onCheckedChange = { value -> onUpdate { it.copy(copyOnSelect = value) } },
          modifier = Modifier.testTag("copy-on-select"),
        )
      }
    }
    Section("File tree") {
      SettingRow("Font size") {
        FontSizeSlider(
          size = settings.treeFontSize,
          range = TreeFontSizeRange,
          tagPrefix = "tree-font-size",
          onChange = { value -> onUpdate { it.copy(treeFontSize = value) } },
        )
      }
    }
    Section("Git") {
      GitSection(settings.gitPath, gitStatus, onCommitGitPath, onChooseGit)
    }
    Section("Keyboard Shortcuts") {
      KeyboardShortcutList(
        resolved = resolvedShortcuts,
        state = shortcutsState,
        onCommit = { id, chord -> onUpdate { it.withShortcut(keymap, id, chord) } },
        onRestoreDefaults = { onUpdate { it.withDefaultShortcuts(keymap) } },
      )
    }
    SettingsFileFooter(settingsFilePath, onRevealSettingsFile)
  }
}

@Composable
private fun LoadBanner(load: SettingsLoad, onReveal: () -> Unit) {
  val colors = LocalDiffColors.current
  val message = when (load) {
    SettingsLoad.PartiallyReset -> PARTIAL_MESSAGE
    else -> UNREADABLE_MESSAGE
  }
  Row(
    modifier = Modifier
      .testTag("settings-load-error")
      .fillMaxWidth()
      .background(colors.removedBackground)
      .padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    StatusImage(KdvIcons.XCircle, "settings-load-error-icon", colors.removedText)
    BasicText(
      text = message,
      style = TextStyle(color = colors.removedText, fontSize = 13.sp),
      modifier = Modifier.weight(1f),
    )
    MacPushButton("Show in Finder", onReveal, Modifier.testTag("settings-reveal-banner"))
  }
}

@Composable
private fun SettingsFileFooter(path: String, onReveal: () -> Unit) {
  val colors = LocalDiffColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BasicText(
      text = "Settings are stored in $path",
      style = TextStyle(color = colors.mutedText, fontSize = 12.sp),
      maxLines = 1,
      overflow = TextOverflow.MiddleEllipsis,
      modifier = Modifier.testTag("settings-file-path").weight(1f),
    )
    MacPushButton("Show in Finder", onReveal, Modifier.testTag("settings-reveal"))
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    BasicText(
      text = title,
      style = TextStyle(color = LocalDiffColors.current.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
    content()
  }
}

@Composable
private fun SettingRow(label: String, description: String? = null, control: @Composable () -> Unit) {
  val colors = LocalDiffColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      BasicText(text = label, style = TextStyle(color = colors.text, fontSize = 13.sp))
      if (description != null) {
        BasicText(text = description, style = TextStyle(color = colors.mutedText, fontSize = 12.sp))
      }
    }
    control()
  }
}

@Composable
private fun FontSizeSlider(size: Int, range: IntRange, tagPrefix: String, onChange: (Int) -> Unit) {
  val colors = LocalDiffColors.current
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    BasicText(text = "A", style = TextStyle(color = colors.mutedText, fontSize = 11.sp))
    MacSlider(value = size, range = range, onValueChange = onChange, modifier = Modifier.testTag("$tagPrefix-slider"))
    BasicText(text = "A", style = TextStyle(color = colors.mutedText, fontSize = 17.sp))
    BasicText(
      text = size.toString(),
      style = TextStyle(color = colors.text, fontSize = 13.sp, textAlign = TextAlign.End),
      modifier = Modifier.testTag("$tagPrefix-value").width(28.dp),
    )
  }
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
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingRow("Executable", "Leave empty to detect git automatically") {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MacTextField(
          value = text,
          onValueChange = { text = it },
          containerModifier = Modifier.width(240.dp),
          modifier = Modifier
            .testTag("git-path-field")
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
        MacPushButton("Choose…", onChoose, Modifier.testTag("git-choose"))
      }
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

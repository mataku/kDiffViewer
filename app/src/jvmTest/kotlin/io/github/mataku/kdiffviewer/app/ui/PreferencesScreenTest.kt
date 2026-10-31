package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.up
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.withKeysDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.settings.Appearance
import io.github.mataku.kdiffviewer.app.settings.GitStatus
import io.github.mataku.kdiffviewer.app.settings.Settings
import io.github.mataku.kdiffviewer.app.settings.SettingsLoad
import io.github.mataku.kdiffviewer.core.git.GitInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PreferencesScreenTest {
  private val ready = GitStatus.Ready(GitInfo("/opt/git", "git version 2.47.0"))

  private class Harness {
    val transforms = mutableListOf<(Settings) -> Settings>()
    val commits = mutableListOf<String>()
    var chooseCount = 0
    var revealCount = 0
    fun last(base: Settings = Settings()) = transforms.last()(base)
  }

  private fun ComposeUiTest.show(
    harness: Harness,
    settings: Settings = Settings(),
    load: SettingsLoad = SettingsLoad.Ok,
    path: String = "~/settings.json",
    status: GitStatus = ready,
    families: List<String>? = listOf("Menlo", "Monaco", "Arial"),
    titleBarInset: Dp = 0.dp,
  ) {
    setContent {
      KdvTheme(colorTheme = ColorTheme.Light) {
        PreferencesScreen(
          settings = settings,
          load = load,
          settingsFilePath = path,
          onRevealSettingsFile = { harness.revealCount++ },
          gitStatus = status,
          fontFamilies = families,
          onUpdate = { harness.transforms += it },
          onCommitGitPath = { harness.commits += it },
          onChooseGit = { harness.chooseCount++ },
          titleBarInset = titleBarInset,
        )
      }
    }
  }

  @Test
  fun titleBarInsetKeepsScrollingContentBelowTitleBar() = runComposeUiTest {
    show(Harness(), titleBarInset = 52.dp)
    val content = onNodeWithTag("settings-content").fetchSemanticsNode().boundsInRoot
    assertTrue(content.top >= with(density) { 52.dp.toPx() }, "content=$content")
  }

  @Test
  fun appearanceSelectionUpdatesSettings() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("appearance-Dark").performClick()
    assertEquals(Appearance.Dark, h.last().appearance)
  }

  @Test
  fun dimSelectionUpdatesSettings() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("appearance-Dim").performClick()
    assertEquals(Appearance.Dim, h.last().appearance)
  }

  @Test
  fun clickingPreviewCardSelectsAppearance() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("appearance-preview-Light", useUnmergedTree = true).performClick()
    assertEquals(Appearance.Light, h.last().appearance)
  }

  @Test
  fun allPreviewCardsAreShown() = runComposeUiTest {
    show(Harness())
    listOf("System", "Light", "Dark", "Dim").forEach {
      onNodeWithTag("appearance-preview-$it", useUnmergedTree = true).assertExists()
    }
  }

  @Test
  fun sliderShowsCurrentValue() = runComposeUiTest {
    show(Harness(), Settings(fontSize = 14))
    onNodeWithTag("font-size-value").assertTextEquals("14")
  }

  @Test
  fun sliderKeyboardChangesSize() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 14))
    onNodeWithTag("font-size-slider").performSemanticsAction(SemanticsActions.RequestFocus)
    onNodeWithTag("font-size-slider").performKeyInput { pressKey(Key.DirectionRight) }
    assertEquals(15, h.last(Settings(fontSize = 14)).fontSize)
    onNodeWithTag("font-size-slider").performKeyInput { pressKey(Key.DirectionUp) }
    assertEquals(15, h.last(Settings(fontSize = 14)).fontSize)
    assertEquals(2, h.transforms.size)
    onNodeWithTag("font-size-slider").performKeyInput { pressKey(Key.DirectionLeft) }
    assertEquals(13, h.last(Settings(fontSize = 14)).fontSize)
    onNodeWithTag("font-size-slider").performKeyInput { pressKey(Key.DirectionDown) }
    assertEquals(13, h.last(Settings(fontSize = 14)).fontSize)
    assertEquals(4, h.transforms.size)
  }

  @Test
  fun sliderKeyboardStopsAtLowerBound() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 8))
    onNodeWithTag("font-size-slider").performSemanticsAction(SemanticsActions.RequestFocus)
    onNodeWithTag("font-size-slider").performKeyInput { pressKey(Key.DirectionLeft) }
    assertEquals(0, h.transforms.size)
  }

  @Test
  fun sliderKeyboardStopsAtUpperBound() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 32))
    onNodeWithTag("font-size-slider").performSemanticsAction(SemanticsActions.RequestFocus)
    onNodeWithTag("font-size-slider").performKeyInput { pressKey(Key.DirectionRight) }
    assertEquals(0, h.transforms.size)
  }

  @Test
  fun sliderSetProgressSemantics() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 12))
    onNodeWithTag("font-size-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
    assertEquals(20, h.last().fontSize)
  }

  @Test
  fun sliderSetProgressIsClampedAndDeduplicated() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 12))
    onNodeWithTag("font-size-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(99f) }
    assertEquals(32, h.last().fontSize)
    onNodeWithTag("font-size-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
    assertEquals(1, h.transforms.size)
  }

  @Test
  fun sliderExposesRangeSemantics() = runComposeUiTest {
    show(Harness(), Settings(fontSize = 14))
    onNodeWithTag("font-size-slider").assert(
      SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(14f, 8f..32f, 23)),
    )
  }

  @Test
  fun draggingSliderAppliesEachIntegerStepOnce() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 8))
    onNodeWithTag("font-size-slider").performTouchInput {
      down(Offset(0f, centerY))
      moveTo(Offset(width.toFloat(), centerY))
      up()
    }
    val sizes = h.transforms.map { it(Settings()).fontSize }
    assertEquals(32, sizes.last())
    assertEquals(sizes.distinct(), sizes)
  }

  @Test
  fun clickingTrackJumpsToValue() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 8))
    onNodeWithTag("font-size-slider").performTouchInput { click(Offset(width.toFloat(), centerY)) }
    assertEquals(32, h.last().fontSize)
  }

  @Test
  fun treeSliderShowsCurrentValue() = runComposeUiTest {
    show(Harness(), Settings(treeFontSize = 15))
    onNodeWithTag("tree-font-size-value").assertTextEquals("15")
  }

  @Test
  fun treeSliderSetProgressSemantics() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(treeFontSize = 13))
    onNodeWithTag("tree-font-size-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(16f) }
    assertEquals(16, h.last().treeFontSize)
  }

  @Test
  fun tabWidthSelection() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("tab-width-4").performClick()
    assertEquals(4, h.last().tabWidth)
  }

  private val hostShortcuts = AppShortcuts.Host
  private val primary = if (HostKeymap == Keymap.Mac) Key.MetaLeft else Key.CtrlLeft

  private fun ComposeUiTest.startRecording(id: ShortcutId) {
    onNodeWithTag("shortcut-${id.settingsKey}").performScrollTo().performClick()
    waitForIdle()
  }

  @Test
  fun keyboardShortcutsSectionListsShortcuts() = runComposeUiTest {
    show(Harness())
    onNodeWithTag("keyboard-shortcuts").performScrollTo()
    ShortcutId.entries.forEach { id ->
      onNodeWithText(id.title).assertExists()
      onNodeWithTag("shortcut-${id.settingsKey}").assertTextEquals(hostShortcuts[id].label)
    }
  }

  @Test
  fun recordingAShortcutSavesItForTheHostPlatform() = runComposeUiTest {
    val h = Harness()
    show(h)
    startRecording(ShortcutId.Reload)
    onNodeWithTag("shortcut-reload").assertTextEquals("Type Shortcut")
    onNodeWithTag("shortcut-reload").performKeyInput { withKeysDown(listOf(primary, Key.ShiftLeft)) { pressKey(Key.K) } }
    val expected = HostKeymap.chord(Key.K, ctrl = HostKeymap != Keymap.Mac, meta = HostKeymap == Keymap.Mac, shift = true)
    assertEquals(expected, h.last().resolveShortcuts(HostKeymap).shortcuts.reload)
  }

  @Test
  fun reservedChordIsRejectedInline() = runComposeUiTest {
    val h = Harness()
    show(h)
    startRecording(ShortcutId.Reload)
    onNodeWithTag("shortcut-reload").performKeyInput { withKeyDown(primary) { pressKey(Key.Q) } }
    onNodeWithTag("shortcut-problem-reload").assertTextEquals("Reserved by the system or a menu item")
    onNodeWithTag("shortcut-reload").assertTextEquals("Type Shortcut")
    assertTrue(h.transforms.isEmpty())
  }

  @Test
  fun chordUsedByAnotherShortcutIsRejectedInline() = runComposeUiTest {
    val h = Harness()
    show(h)
    startRecording(ShortcutId.Reload)
    onNodeWithTag("shortcut-reload").performKeyInput { withKeysDown(listOf(primary, Key.ShiftLeft)) { pressKey(Key.P) } }
    onNodeWithTag("shortcut-problem-reload").assertTextEquals("Used by Switch Write and Preview")
    assertTrue(h.transforms.isEmpty())
  }

  @Test
  fun escapeCancelsRecording() = runComposeUiTest {
    val h = Harness()
    show(h)
    startRecording(ShortcutId.Reload)
    onNodeWithTag("shortcut-reload").performKeyInput { pressKey(Key.Escape) }
    waitForIdle()
    onNodeWithTag("shortcut-reload").assertTextEquals(hostShortcuts.reload.label)
    assertTrue(h.transforms.isEmpty())
  }

  @Test
  fun invalidSettingsEntryIsShownOnItsRow() = runComposeUiTest {
    val stored = JsonObject(mapOf(HostKeymap.settingsKey to JsonObject(mapOf("reload" to JsonPrimitive("comd+r")))))
    show(Harness(), Settings(shortcuts = stored))
    onNodeWithTag("shortcut-problem-reload").performScrollTo().assertTextEquals("settings.json: Not a valid shortcut: \"comd+r\"")
    onNodeWithTag("shortcut-reload").assertTextEquals(hostShortcuts.reload.label)
  }

  @Test
  fun restoreDefaultsClearsTheHostBlock() = runComposeUiTest {
    val h = Harness()
    val stored = JsonObject(mapOf(HostKeymap.settingsKey to JsonObject(mapOf("reload" to JsonPrimitive("comd+r")))))
    show(h, Settings(shortcuts = stored))
    onNodeWithTag("shortcuts-restore-defaults").performScrollTo().performClick()
    assertNull(h.last(Settings(shortcuts = stored)).shortcutBlock(HostKeymap))
  }

  @Test
  fun copyOnSelectSwitchUpdatesSettings() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("copy-on-select").performClick()
    assertEquals(true, h.last().copyOnSelect)
  }

  @Test
  fun enterInGitFieldCommits() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("git-path-field").performTextInput("/bad")
    onNodeWithTag("git-path-field").performKeyInput { pressKey(Key.Enter) }
    assertEquals(listOf("/bad"), h.commits)
  }

  @Test
  fun focusLossCommitsGitField() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("git-path-field").performClick()
    onNodeWithTag("git-path-field").performTextInput("/bad")
    onNodeWithTag("git-choose").performClick()
    waitForIdle()
    assertEquals(listOf("/bad"), h.commits)
  }

  @Test
  fun numpadEnterInGitFieldCommits() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("git-path-field").performTextInput("/bad")
    onNodeWithTag("git-path-field").performKeyInput { pressKey(Key.NumPadEnter) }
    assertEquals(listOf("/bad"), h.commits)
  }

  @Test
  fun disposingWithEditedGitFieldCommits() = runComposeUiTest {
    val h = Harness()
    var visible by mutableStateOf(true)
    setContent {
      if (visible) {
        KdvTheme(colorTheme = ColorTheme.Light) {
          PreferencesScreen(
            settings = Settings(),
            load = SettingsLoad.Ok,
            settingsFilePath = "",
            onRevealSettingsFile = {},
            gitStatus = ready,
            fontFamilies = null,
            onUpdate = {},
            onCommitGitPath = { h.commits += it },
            onChooseGit = {},
          )
        }
      }
    }
    onNodeWithTag("git-path-field").performTextInput("/bad")
    visible = false
    waitForIdle()
    assertEquals(listOf("/bad"), h.commits)
  }

  @Test
  fun disposingWithoutEditsDoesNotCommit() = runComposeUiTest {
    val h = Harness()
    var visible by mutableStateOf(true)
    setContent {
      if (visible) {
        KdvTheme(colorTheme = ColorTheme.Light) {
          PreferencesScreen(
            settings = Settings(),
            load = SettingsLoad.Ok,
            settingsFilePath = "",
            onRevealSettingsFile = {},
            gitStatus = ready,
            fontFamilies = null,
            onUpdate = {},
            onCommitGitPath = { h.commits += it },
            onChooseGit = {},
          )
        }
      }
    }
    waitForIdle()
    visible = false
    waitForIdle()
    assertEquals(emptyList(), h.commits)
  }

  @Test
  fun rejectedStatusShowsError() = runComposeUiTest {
    show(Harness(), status = GitStatus.Rejected("/bad", null))
    onNodeWithTag("git-status").assertTextEquals("Not a usable git executable")
  }

  @Test
  fun configuredUnavailableShowsError() = runComposeUiTest {
    show(Harness(), Settings(gitPath = "/gone"), status = GitStatus.Rejected("/gone", null))
    onNodeWithTag("git-path-field").assertTextEquals("/gone")
    onNodeWithTag("git-status").assertTextEquals("Not a usable git executable")
  }

  @Test
  fun autoDetectFailedShowsError() = runComposeUiTest {
    show(Harness(), status = GitStatus.AutoDetectFailed(null))
    onNodeWithTag("git-status").assertTextEquals("git was not found")
    onNodeWithTag("git-status-icon-error", useUnmergedTree = true).assertExists()
  }

  @Test
  fun rejectedFieldHasErrorSemantics() = runComposeUiTest {
    show(Harness(), status = GitStatus.Rejected("/bad", null))
    onNodeWithTag("git-path-field").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
  }

  @Test
  fun readyFieldHasNoErrorSemantics() = runComposeUiTest {
    show(Harness())
    onNodeWithTag("git-path-field").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
  }

  @Test
  fun readyShowsOkIcon() = runComposeUiTest {
    show(Harness(), Settings(gitPath = "/opt/git"))
    onNodeWithTag("git-status-icon-ok", useUnmergedTree = true).assertExists()
  }

  @Test
  fun autoDetectedReadyHasNoIcon() = runComposeUiTest {
    show(Harness())
    onNodeWithTag("git-status-icon-ok", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun configuredReadyShowsVersion() = runComposeUiTest {
    show(Harness(), Settings(gitPath = "/opt/git"))
    onNodeWithTag("git-status").assertTextEquals("git version 2.47.0")
  }

  @Test
  fun notFoundAndCheckingStatus() = runComposeUiTest {
    show(Harness(), status = GitStatus.NotFound)
    onNodeWithTag("git-status").assertTextEquals("git was not found")
  }

  @Test
  fun checkingStatusShowsNothing() = runComposeUiTest {
    show(Harness(), status = GitStatus.Checking)
    onNodeWithTag("git-status").assertTextEquals("")
    onNodeWithTag("git-status-icon-ok", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag("git-status-icon-error", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun emptyFieldShowsAutoDetectedPath() = runComposeUiTest {
    show(Harness())
    onNodeWithText("Auto-detected: /opt/git").assertIsDisplayed()
  }

  @Test
  fun partialBannerText() = runComposeUiTest {
    show(Harness(), load = SettingsLoad.PartiallyReset)
    onNodeWithTag("settings-load-error", useUnmergedTree = true)
      .assertExists()
    onNodeWithText("Some settings in settings.json were invalid and were reset to defaults.").assertExists()
  }

  @Test
  fun unreadableBannerText() = runComposeUiTest {
    show(Harness(), load = SettingsLoad.Unreadable)
    onNodeWithText("Could not read settings.json. Using defaults; changes made here are not saved until the file is fixed.").assertExists()
  }

  @Test
  fun noBannerWhenOk() = runComposeUiTest {
    show(Harness())
    onNodeWithTag("settings-load-error").assertDoesNotExist()
    onNodeWithTag("settings-reveal-banner").assertDoesNotExist()
  }

  @Test
  fun bannerRevealCallsHandler() = runComposeUiTest {
    val h = Harness()
    show(h, load = SettingsLoad.Unreadable)
    onNodeWithTag("settings-reveal-banner").performClick()
    assertEquals(1, h.revealCount)
  }

  @Test
  fun footerShowsPathAndReveals() = runComposeUiTest {
    val h = Harness()
    show(h, path = "~/Library/Application Support/kDiffViewer/settings.json")
    onNodeWithTag("settings-file-path").assertTextEquals("Settings are stored in ~/Library/Application Support/kDiffViewer/settings.json")
    onNodeWithTag("settings-reveal").performScrollTo().performClick()
    assertEquals(1, h.revealCount)
  }

  @Test
  fun fontFilterNarrowsList() = runComposeUiTest {
    show(Harness())
    onNodeWithTag("font-picker").performClick()
    onNodeWithTag("font-filter").performTextInput("N")
    onNodeWithTag("font-item-Menlo").assertIsDisplayed()
    onNodeWithTag("font-item-Monaco").assertIsDisplayed()
    onNodeWithTag("font-item-Arial").assertDoesNotExist()
  }

  @Test
  fun choosingFontUpdatesFamily() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("font-picker").performClick()
    onNodeWithTag("font-item-Monaco").performClick()
    assertEquals("Monaco", h.last().fontFamily)
    onNodeWithTag("font-filter").assertDoesNotExist()
  }

  @Test
  fun choosingSystemMonospaceClearsFamily() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontFamily = "Menlo"))
    onNodeWithTag("font-picker").performClick()
    onNodeWithTag("font-item-System monospace").performClick()
    assertNull(h.last(Settings(fontFamily = "Menlo")).fontFamily)
  }

  @Test
  fun fontListUnavailableShowsOnlySystemMonospace() = runComposeUiTest {
    show(Harness(), families = null)
    onNodeWithTag("font-picker").performClick()
    onNodeWithTag("font-item-System monospace").assertIsDisplayed()
    onNodeWithTag("font-item-Menlo").assertDoesNotExist()
  }

  @Test
  fun chooseButtonInvokesCallback() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("git-choose").performClick()
    assertEquals(1, h.chooseCount)
  }
}

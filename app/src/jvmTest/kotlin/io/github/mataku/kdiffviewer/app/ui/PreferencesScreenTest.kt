package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.mataku.kdiffviewer.app.settings.Appearance
import io.github.mataku.kdiffviewer.app.settings.GitStatus
import io.github.mataku.kdiffviewer.app.settings.Settings
import io.github.mataku.kdiffviewer.core.git.GitInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class PreferencesScreenTest {
  private val ready = GitStatus.Ready(GitInfo("/opt/git", "git version 2.47.0"))

  private class Harness {
    val transforms = mutableListOf<(Settings) -> Settings>()
    val commits = mutableListOf<String>()
    var chooseCount = 0
    fun last(base: Settings = Settings()) = transforms.last()(base)
  }

  private fun ComposeUiTest.show(
    harness: Harness,
    settings: Settings = Settings(),
    loadError: Boolean = false,
    status: GitStatus = ready,
    families: List<String>? = listOf("Menlo", "Monaco", "Arial"),
  ) {
    setContent {
      KdvTheme(darkTheme = false) {
        PreferencesScreen(
          settings = settings,
          loadError = loadError,
          gitStatus = status,
          fontFamilies = families,
          onUpdate = { harness.transforms += it },
          onCommitGitPath = { harness.commits += it },
          onChooseGit = { harness.chooseCount++ },
        )
      }
    }
  }

  @Test
  fun appearanceSelectionUpdatesSettings() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("appearance-Dark").performClick()
    assertEquals(Appearance.Dark, h.last().appearance)
  }

  @Test
  fun fontSizeStepperStopsAtBounds() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 32))
    onNodeWithTag("font-size-increase").assertIsNotEnabled()
    onNodeWithTag("font-size-decrease").assertIsEnabled()
  }

  @Test
  fun fontSizeStepperLowerBound() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 8))
    onNodeWithTag("font-size-decrease").assertIsNotEnabled()
    onNodeWithTag("font-size-increase").performClick()
    assertEquals(9, h.last(Settings(fontSize = 8)).fontSize)
  }

  @Test
  fun tabWidthSelection() = runComposeUiTest {
    val h = Harness()
    show(h)
    onNodeWithTag("tab-width-4").performClick()
    assertEquals(4, h.last().tabWidth)
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
        KdvTheme(darkTheme = false) {
          PreferencesScreen(
            settings = Settings(),
            loadError = false,
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
        KdvTheme(darkTheme = false) {
          PreferencesScreen(
            settings = Settings(),
            loadError = false,
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
  fun fontSizeStepperAppliesRelativeToLatestSettings() = runComposeUiTest {
    val h = Harness()
    show(h, Settings(fontSize = 12))
    onNodeWithTag("font-size-increase").performClick()
    assertEquals(21, h.last(Settings(fontSize = 20)).fontSize)
    onNodeWithTag("font-size-decrease").performClick()
    assertEquals(19, h.last(Settings(fontSize = 20)).fontSize)
    onNodeWithTag("font-size-increase").performClick()
    assertEquals(32, h.last(Settings(fontSize = 32)).fontSize)
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
  fun loadErrorShowsBanner() = runComposeUiTest {
    show(Harness(), loadError = true)
    onNodeWithTag("settings-load-error").assertTextEquals("Could not read settings file; using defaults")
  }

  @Test
  fun noBannerWithoutLoadError() = runComposeUiTest {
    show(Harness())
    onNodeWithTag("settings-load-error").assertDoesNotExist()
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

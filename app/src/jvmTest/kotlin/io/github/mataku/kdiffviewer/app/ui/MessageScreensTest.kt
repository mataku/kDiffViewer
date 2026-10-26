package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.mataku.kdiffviewer.app.ViewerError
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MessageScreensTest {
  @Test
  fun openScreenHasButton() = runComposeUiTest {
    var opened = 0
    setContent { OpenRepositoryScreen(onOpen = { opened++ }) }
    onNodeWithText("Open Repository…").performClick()
    assertEquals(1, opened)
  }

  @Test
  fun gitNotFoundPointsToSettings() = runComposeUiTest {
    var opened = 0
    setContent { ErrorScreen(ViewerError.GitNotFound, onOpenAnother = {}, onOpenSettings = { opened++ }) }
    onNodeWithText("git was not found. Choose a git executable in Settings.").assertIsDisplayed()
    onNodeWithText("Open Settings…").performClick()
    assertEquals(1, opened)
  }

  @Test
  fun gitUnavailableShowsPath() = runComposeUiTest {
    setContent { ErrorScreen(ViewerError.GitUnavailable("/gone"), onOpenAnother = {}, onOpenSettings = {}) }
    onNodeWithText("The configured git (/gone) cannot be run.").assertIsDisplayed()
    onNodeWithText("Open Settings…").assertIsDisplayed()
  }

  @Test
  fun notARepositoryMessage() = runComposeUiTest {
    var opened = 0
    setContent { ErrorScreen(ViewerError.NotARepository("/x"), onOpenAnother = { opened++ }, onOpenSettings = {}) }
    onNodeWithText("/x is not a git repository.").assertIsDisplayed()
    onNodeWithText("Open Another Folder…").performClick()
    assertEquals(1, opened)
  }

  @Test
  fun gitFailedShowsStderr() = runComposeUiTest {
    setContent { ErrorScreen(ViewerError.GitFailed("fatal: bad"), onOpenAnother = {}, onOpenSettings = {}) }
    onNodeWithText("fatal: bad").assertIsDisplayed()
  }

  @Test
  fun gitFailedWithBlankStderrShowsExitCode() = runComposeUiTest {
    setContent { ErrorScreen(ViewerError.GitFailed("  \n", 128), onOpenAnother = {}, onOpenSettings = {}) }
    onNodeWithText("git exited with code 128").assertIsDisplayed()
  }
}

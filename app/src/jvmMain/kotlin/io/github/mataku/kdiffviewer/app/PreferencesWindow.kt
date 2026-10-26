package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import io.github.mataku.kdiffviewer.app.settings.GitStatus
import io.github.mataku.kdiffviewer.app.settings.Settings
import io.github.mataku.kdiffviewer.app.settings.SettingsLoad
import io.github.mataku.kdiffviewer.app.settings.defaultSettingsFile
import io.github.mataku.kdiffviewer.app.settings.displayPath
import io.github.mataku.kdiffviewer.app.settings.revealInFinder
import io.github.mataku.kdiffviewer.app.ui.KdvTheme
import io.github.mataku.kdiffviewer.app.ui.PreferencesScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment

@Composable
fun PreferencesWindow(
  focusRequests: Int,
  onClose: () -> Unit,
  settings: Settings,
  load: SettingsLoad,
  gitStatus: GitStatus,
  onUpdate: ((Settings) -> Settings) -> Unit,
  onCommitGitPath: (String) -> Unit,
) {
  Window(
    onCloseRequest = onClose,
    title = "Settings",
    resizable = false,
    state = rememberWindowState(size = DpSize(640.dp, 600.dp)),
  ) {
    LaunchedEffect(settings.appearance) { window.applyAppearance(settings.appearance) }
    LaunchedEffect(focusRequests) { window.toFront() }
    val fontFamilies by produceState<List<String>?>(null) {
      value = withContext(Dispatchers.IO) {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
      }
    }
    val scope = rememberCoroutineScope()
    val settingsFile = remember { defaultSettingsFile() }
    KdvTheme(darkTheme = settings.appearance.isDark()) {
      PreferencesScreen(
        settings = settings,
        load = load,
        settingsFilePath = settingsFile.displayPath(),
        onRevealSettingsFile = {
          scope.launch(Dispatchers.IO) {
            try {
              revealInFinder(settingsFile)
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              e.printStackTrace()
            }
          }
        },
        gitStatus = gitStatus,
        fontFamilies = fontFamilies,
        onUpdate = onUpdate,
        onCommitGitPath = onCommitGitPath,
        onChooseGit = { chooseGitExecutable()?.let(onCommitGitPath) },
      )
    }
  }
}

private fun chooseGitExecutable(): String? {
  val dialog = FileDialog(null as Frame?, "Choose git", FileDialog.LOAD)
  dialog.isVisible = true
  val directory = dialog.directory ?: return null
  val name = dialog.file ?: return null
  return java.io.File(directory, name).path
}

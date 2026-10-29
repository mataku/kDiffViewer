package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import io.github.mataku.kdiffviewer.app.settings.FileSettingsStorage
import io.github.mataku.kdiffviewer.app.settings.SettingsRepository
import io.github.mataku.kdiffviewer.app.settings.WindowState
import io.github.mataku.kdiffviewer.app.settings.WindowStateCodec
import io.github.mataku.kdiffviewer.app.settings.defaultSettingsFile
import io.github.mataku.kdiffviewer.app.settings.defaultWindowStateFile
import io.github.mataku.kdiffviewer.app.settings.mainSizeOrNull
import io.github.mataku.kdiffviewer.app.settings.treeWidthOrNull
import io.github.mataku.kdiffviewer.app.settings.updated
import io.github.mataku.kdiffviewer.app.ui.DefaultTreeWidth
import io.github.mataku.kdiffviewer.app.ui.MaxTreeWidth
import io.github.mataku.kdiffviewer.app.ui.MinTreeWidth
import io.github.mataku.kdiffviewer.core.git.GitLocation
import io.github.mataku.kdiffviewer.core.git.GitLocator
import io.github.mataku.kdiffviewer.core.git.locateOnHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.awt.GraphicsEnvironment
import kotlin.system.exitProcess
import androidx.compose.ui.window.WindowState as ComposeWindowState

fun main(args: Array<String>) {
  System.setProperty("apple.awt.application.name", "kDiffViewer")
  System.setProperty("apple.awt.application.appearance", "system")
  val repository = SettingsRepository(
    FileSettingsStorage(defaultSettingsFile()),
    CoroutineScope(SupervisorJob() + Dispatchers.IO),
    Dispatchers.IO,
  )
  val location = GitLocator.locateOnHost(repository.settings.value.gitPath)
  val factory = GitBackendFactory(highlightEnabled = System.getProperty("kdv.highlight") != "false")
  val windowStateStorage = FileSettingsStorage(defaultWindowStateFile())
  val saved = loadWindowState(windowStateStorage)
  val windowState = ComposeWindowState(size = initialMainSize(saved) ?: DpSize(800.dp, 600.dp))
  val treeWidth = mutableStateOf(saved.treeWidthOrNull(MinTreeWidth, MaxTreeWidth) ?: DefaultTreeWidth)
  runApplication(args, repository, location, factory, windowState, treeWidth)
  saveWindowState(windowStateStorage, saved, windowState, treeWidth.value)
  runBlocking { repository.flush() }
  exitProcess(0)
}

private val MinMainSize = DpSize(640.dp, 400.dp)

private fun loadWindowState(storage: FileSettingsStorage): WindowState = try {
  WindowStateCodec.decode(storage.read())
} catch (e: Exception) {
  e.printStackTrace()
  WindowState()
}

private fun initialMainSize(saved: WindowState): DpSize? = try {
  val max = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.let { DpSize(it.width.dp, it.height.dp) }
  saved.mainSizeOrNull(MinMainSize, max)
} catch (e: Exception) {
  e.printStackTrace()
  null
}

private fun saveWindowState(storage: FileSettingsStorage, saved: WindowState, windowState: ComposeWindowState, treeWidth: Dp) {
  val size = windowState.size
  val floating = windowState.placement == WindowPlacement.Floating && !windowState.isMinimized && size.isSpecified
  val next = saved.updated(size.takeIf { floating }, treeWidth)
  if (next == saved) return
  try {
    storage.write(WindowStateCodec.encode(next))
  } catch (e: Exception) {
    e.printStackTrace()
  }
}

private fun runApplication(
  args: Array<String>,
  repository: SettingsRepository,
  location: GitLocation,
  factory: GitBackendFactory,
  windowState: ComposeWindowState,
  treeWidth: MutableState<Dp>,
) = application(exitProcessOnExit = false) {
  val appState = rememberKdvAppState(args, repository, location, factory, exit = ::exitApplication)
  val settings by repository.settings.collectAsState()
  val nativePreferences = rememberNativePreferencesSupported()
  DesktopHandlersEffect(nativePreferences, onPreferences = appState::openSettings, onQuit = appState::requestQuit)
  MainWindow(appState, repository, settings, factory, windowState, treeWidth, nativePreferences)
  if (appState.settingsOpen) {
    val gitStatus by appState.gitBackend.status.collectAsState()
    PreferencesWindow(
      focusRequests = appState.settingsFocusRequests,
      onClose = appState::closeSettings,
      settings = settings,
      load = repository.load,
      gitStatus = gitStatus,
      onUpdate = repository::update,
      onCommitGitPath = appState.gitBackend::commit,
    )
  }
}

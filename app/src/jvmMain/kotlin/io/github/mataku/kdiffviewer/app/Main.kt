package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.mataku.kdiffviewer.app.settings.FileSettingsStorage
import io.github.mataku.kdiffviewer.app.settings.GitBackendController
import io.github.mataku.kdiffviewer.app.settings.SettingsRepository
import io.github.mataku.kdiffviewer.app.settings.defaultSettingsFile
import io.github.mataku.kdiffviewer.app.ui.ErrorScreen
import io.github.mataku.kdiffviewer.app.ui.KdvTheme
import io.github.mataku.kdiffviewer.app.ui.LocalTitleBarRegions
import io.github.mataku.kdiffviewer.app.ui.OpenRepositoryScreen
import io.github.mataku.kdiffviewer.app.ui.TitleBarHeight
import io.github.mataku.kdiffviewer.app.ui.ViewerScreen
import io.github.mataku.kdiffviewer.app.ui.resolveFontFamily
import io.github.mataku.kdiffviewer.core.git.GitLocation
import io.github.mataku.kdiffviewer.core.git.GitLocator
import io.github.mataku.kdiffviewer.core.git.locateOnHost
import io.github.mataku.kdiffviewer.core.git.probeGit
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.Desktop

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
  runApplication(args, repository, location, factory)
}

private fun runApplication(
  args: Array<String>,
  repository: SettingsRepository,
  location: GitLocation,
  factory: GitBackendFactory,
) = application {
  val scope = remember { MainScope() }
  DisposableEffect(Unit) { onDispose { scope.cancel() } }
  val state = remember {
    val backend = (location as? GitLocation.Found)?.let { factory.create(it.info) }
    val missingSourceError = (location as? GitLocation.ConfiguredUnavailable)?.let { ViewerError.GitUnavailable(it.path) } ?: ViewerError.GitNotFound
    DiffViewerState(
      source = backend?.source,
      scope = scope,
      highlightPipeline = backend?.highlightPipeline,
      missingSourceError = missingSourceError,
    ).also { viewerState ->
      parseLaunchArgs(args, System.getProperty("user.dir"))?.let(viewerState::open)
    }
  }
  val controller = remember {
    GitBackendController(
      initial = location,
      settings = repository,
      scope = scope,
      ioDispatcher = Dispatchers.IO,
      probe = ::probeGit,
      locate = { GitLocator.locateOnHost(it) },
      createBackend = factory::create,
      onBackend = { state.setBackend(it?.source, it?.highlightPipeline) },
    )
  }
  val uiState by state.uiState.collectAsState()
  val settings by repository.settings.collectAsState()
  val gitStatus by controller.status.collectAsState()
  var settingsOpen by remember { mutableStateOf(false) }
  var focusRequests by remember { mutableIntStateOf(0) }
  val openSettings = {
    settingsOpen = true
    focusRequests++
  }
  val openFolder: () -> Unit = { pickFolder()?.let { state.open(RepoPath(it)) } }
  val nativePreferences = remember {
    Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_PREFERENCES)
  }
  if (nativePreferences) {
    DisposableEffect(Unit) {
      Desktop.getDesktop().setPreferencesHandler { openSettings() }
      onDispose { Desktop.getDesktop().setPreferencesHandler(null) }
    }
  }

  Window(onCloseRequest = ::exitApplication, title = "kDiffViewer") {
    LaunchedEffect(settings.appearance) { window.applyAppearance(settings.appearance) }
    val titleBarRegions = remember { installCustomTitleBar(window, TitleBarHeight) }
    val titleBarInset = if (titleBarRegions != null) TitleBarHeight else 0.dp
    MenuBar {
      Menu("File") {
        Item("Open…", shortcut = KeyShortcut(Key.O, meta = true), onClick = openFolder)
        if (!nativePreferences) {
          Item("Settings…", shortcut = KeyShortcut(Key.Comma, meta = true), onClick = { openSettings() })
        }
      }
      Menu("View") {
        Item("Reload", shortcut = KeyShortcut(Key.R, meta = true), onClick = state::reload)
      }
    }
    val fontFamily = remember(settings.fontFamily) { resolveFontFamily(settings.fontFamily) }
    CompositionLocalProvider(LocalTitleBarRegions provides titleBarRegions) {
      KdvTheme(
        darkTheme = settings.appearance.isDark(),
        fontFamily = fontFamily,
        fontSize = settings.fontSize,
        tabWidth = settings.tabWidth,
      ) {
        val status = uiState.status
        when {
          uiState.repo == null -> OpenRepositoryScreen(onOpen = openFolder)
          status is LoadStatus.Failed -> ErrorScreen(status.error, onOpenAnother = openFolder, onOpenSettings = { openSettings() })
          else -> ViewerScreen(uiState, onReload = state::reload, titleBarInset = titleBarInset)
        }
      }
    }
  }

  if (settingsOpen) {
    PreferencesWindow(
      focusRequests = focusRequests,
      onClose = { settingsOpen = false },
      settings = settings,
      loadError = repository.loadError,
      gitStatus = gitStatus,
      onUpdate = repository::update,
      onCommitGitPath = controller::commit,
    )
  }
}

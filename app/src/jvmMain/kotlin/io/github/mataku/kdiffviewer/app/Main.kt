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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import io.github.mataku.kdiffviewer.app.highlight.LocalCodeHighlighter
import io.github.mataku.kdiffviewer.app.review.ReviewIntegration
import io.github.mataku.kdiffviewer.app.review.ReviewRequestHandler
import io.github.mataku.kdiffviewer.app.review.ReviewState
import io.github.mataku.kdiffviewer.app.review.SessionRegistry
import io.github.mataku.kdiffviewer.app.review.processAlive
import io.github.mataku.kdiffviewer.app.selection.LocalNativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.selection.TextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.settings.FileSettingsStorage
import io.github.mataku.kdiffviewer.app.settings.GitBackendController
import io.github.mataku.kdiffviewer.app.settings.SettingsRepository
import io.github.mataku.kdiffviewer.app.settings.WindowState
import io.github.mataku.kdiffviewer.app.settings.WindowStateCodec
import io.github.mataku.kdiffviewer.app.settings.defaultSettingsFile
import io.github.mataku.kdiffviewer.app.settings.defaultWindowStateFile
import io.github.mataku.kdiffviewer.app.settings.mainSizeOrNull
import io.github.mataku.kdiffviewer.app.ui.ErrorScreen
import io.github.mataku.kdiffviewer.app.ui.KdvTheme
import io.github.mataku.kdiffviewer.app.ui.LocalTitleBarRegions
import io.github.mataku.kdiffviewer.app.ui.OpenRepositoryScreen
import io.github.mataku.kdiffviewer.app.ui.ScrollJumps
import io.github.mataku.kdiffviewer.app.ui.ViewerScreen
import io.github.mataku.kdiffviewer.app.ui.ViewerTitleBarHeight
import io.github.mataku.kdiffviewer.app.ui.markdown.SafeUriHandler
import io.github.mataku.kdiffviewer.app.ui.resolveFontFamily
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewActions
import io.github.mataku.kdiffviewer.app.ui.review.ReviewControls
import io.github.mataku.kdiffviewer.app.ui.scrollJumpOf
import io.github.mataku.kdiffviewer.core.git.GitLocation
import io.github.mataku.kdiffviewer.core.git.GitLocator
import io.github.mataku.kdiffviewer.core.git.locateOnHost
import io.github.mataku.kdiffviewer.core.git.probeGit
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.protocol.PROTOCOL_VERSION
import io.github.mataku.kdiffviewer.protocol.RegistryPaths
import io.github.mataku.kdiffviewer.protocol.WindowInfoResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.nio.file.Path
import kotlin.time.Clock
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
  val windowState = ComposeWindowState(size = loadInitialMainSize(windowStateStorage) ?: DpSize(800.dp, 600.dp))
  runApplication(args, repository, location, factory, windowState)
  saveMainSize(windowStateStorage, windowState)
  runBlocking { repository.flush() }
}

private val MinMainSize = DpSize(640.dp, 400.dp)

private val FallbackTrafficLightsWidth = 78.dp

private fun loadInitialMainSize(storage: FileSettingsStorage): DpSize? = try {
  val max = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.let { DpSize(it.width.dp, it.height.dp) }
  WindowStateCodec.decode(storage.read()).mainSizeOrNull(MinMainSize, max)
} catch (e: Exception) {
  e.printStackTrace()
  null
}

private fun saveMainSize(storage: FileSettingsStorage, windowState: ComposeWindowState) {
  if (windowState.placement != WindowPlacement.Floating || windowState.isMinimized) return
  val size = windowState.size
  if (!size.isSpecified) return
  try {
    storage.write(WindowStateCodec.encode(WindowState(size.width.value.toInt(), size.height.value.toInt())))
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
      loadContext = Dispatchers.Default,
    ).also { viewerState ->
      when (val launch = parseLaunchArgs(args, System.getProperty("user.dir"))) {
        LaunchArgs.None -> Unit
        is LaunchArgs.Open -> viewerState.open(launch.repo, launch.base)
        is LaunchArgs.Invalid -> viewerState.fail(launch.repo, ViewerError.InvalidLaunchArgs(launch.message))
      }
    }
  }
  val review = remember { ReviewState(scope, Dispatchers.Default, Clock.System::now) }
  val reviewSnapshot by review.snapshot.collectAsState()
  val registry = remember {
    SessionRegistry(
      Path.of(RegistryPaths.sessionsDir(System.getProperty("user.home"))),
      ProcessHandle.current().pid(),
      java.time.Instant::now,
      ::processAlive,
    )
  }
  val integration = remember {
    val handler = ReviewRequestHandler(
      review = review,
      reload = { withContext(Dispatchers.Main) { state.reload() } },
      info = {
        WindowInfoResult(ProcessHandle.current().pid(), registry.repoRoot, PROTOCOL_VERSION)
      },
    )
    ReviewIntegration(registry, Dispatchers.IO, Dispatchers.IO, handler::handle)
  }
  LaunchedEffect(Unit) { state.uiState.collect(review::onViewerState) }
  LaunchedEffect(Unit) {
    val started = try {
      withContext(Dispatchers.IO) { integration.start() }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
    if (!started) return@LaunchedEffect
    integration.publishRoots(state.uiState)
  }
  val shutdown: () -> Unit = {
    integration.close()
    exitApplication()
  }
  DisposableEffect(Unit) { onDispose { integration.close() } }
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

  DisposableEffect(Unit) {
    val desktop = Desktop.getDesktop().takeIf { Desktop.isDesktopSupported() && it.isSupported(Desktop.Action.APP_QUIT_HANDLER) }
    desktop?.setQuitHandler { _, response ->
      response.cancelQuit()
      shutdown()
    }
    onDispose { desktop?.setQuitHandler(null) }
  }

  val scrollJumps = remember { ScrollJumps() }
  var diffMode by remember { mutableStateOf(DiffMode.Unified) }
  val changeMode = { mode: DiffMode -> if (mode != diffMode) diffMode = mode }
  var sidebarVisible by remember { mutableStateOf(true) }
  val toggleSidebar = { sidebarVisible = !sidebarVisible }
  var reviewControls by remember { mutableStateOf(ReviewControls.Toolbar) }
  Window(
    onCloseRequest = shutdown,
    title = "kDiffViewer",
    state = windowState,
    onKeyEvent = { event -> scrollJumpOf(event)?.let(scrollJumps::request) ?: false },
  ) {
    LaunchedEffect(settings.appearance) { window.applyAppearance(settings.appearance) }
    val titleBar = remember { installCustomTitleBar(window, ViewerTitleBarHeight) }
    var titleBarLeadingInset by remember { mutableStateOf(0.dp) }
    LaunchedEffect(titleBar) {
      if (titleBar == null) return@LaunchedEffect
      withFrameNanos {}
      titleBarLeadingInset = titleBar.leftInset().takeIf { it > 0f }?.dp ?: FallbackTrafficLightsWidth
    }
    val coordinator = remember { TextSelectionCoordinator(AwtClipboardWriter) { repository.settings.value.copyOnSelect } }
    val nativeMenu = remember(window) { AwtNativeContextMenu(window) }
    MenuBar {
      Menu("File") {
        Item("Open…", shortcut = KeyShortcut(Key.O, meta = true), onClick = openFolder)
        if (!nativePreferences) {
          Item("Settings…", shortcut = KeyShortcut(Key.Comma, meta = true), onClick = { openSettings() })
        }
      }
      Menu("Edit") {
        Item("Copy", shortcut = KeyShortcut(Key.C, meta = true), enabled = coordinator.hasSelection, onClick = { coordinator.copy() })
      }
      Menu("View") {
        RadioButtonItem("Unified", selected = diffMode == DiffMode.Unified, onClick = { changeMode(DiffMode.Unified) })
        RadioButtonItem("Split", selected = diffMode == DiffMode.Split, onClick = { changeMode(DiffMode.Split) })
        Separator()
        Item(
          if (sidebarVisible) "Hide Sidebar" else "Show Sidebar",
          shortcut = KeyShortcut(Key.S, ctrl = true, meta = true),
          enabled = uiState.repo != null && uiState.status !is LoadStatus.Failed,
          onClick = toggleSidebar,
        )
        Menu("Review Controls") {
          RadioButtonItem("Toolbar", selected = reviewControls == ReviewControls.Toolbar, onClick = { reviewControls = ReviewControls.Toolbar })
          RadioButtonItem("Bottom Bar", selected = reviewControls == ReviewControls.BottomBar, onClick = { reviewControls = ReviewControls.BottomBar })
        }
        Separator()
        Item("Reload", shortcut = KeyShortcut(Key.R, meta = true), onClick = state::reload)
      }
    }
    val fontFamily = remember(settings.fontFamily) { resolveFontFamily(settings.fontFamily) }
    val platformUriHandler = LocalUriHandler.current
    val safeUriHandler = remember(platformUriHandler) { SafeUriHandler(platformUriHandler) }
    CompositionLocalProvider(
      LocalTitleBarRegions provides titleBar?.regions,
      LocalReviewActions provides review,
      LocalCodeHighlighter provides factory.codeHighlighter,
      LocalUriHandler provides safeUriHandler,
      LocalTextSelectionCoordinator provides coordinator,
      LocalNativeContextMenu provides nativeMenu,
    ) {
      KdvTheme(
        colorTheme = settings.appearance.colorTheme(),
        fontFamily = fontFamily,
        fontSize = settings.fontSize,
        tabWidth = settings.tabWidth,
        treeFontSize = settings.treeFontSize,
      ) {
        val status = uiState.status
        when {
          uiState.repo == null -> OpenRepositoryScreen(onOpen = openFolder)

          status is LoadStatus.Failed -> ErrorScreen(status.error, onOpenAnother = openFolder, onOpenSettings = { openSettings() })

          else -> ViewerScreen(
            uiState,
            onReload = state::reload,
            titleBarLeadingInset = titleBarLeadingInset,
            sidebarVisible = sidebarVisible,
            onToggleSidebar = toggleSidebar,
            onToggleFile = state::toggleCollapsed,
            onExpandFile = state::expand,
            review = reviewSnapshot,
            loadBranches = state::branches,
            onSwitchBase = state::switchBase,
            scrollJumps = scrollJumps,
            mode = diffMode,
            onModeChange = changeMode,
            reviewControls = reviewControls,
          )
        }
      }
    }
  }

  if (settingsOpen) {
    PreferencesWindow(
      focusRequests = focusRequests,
      onClose = { settingsOpen = false },
      settings = settings,
      load = repository.load,
      gitStatus = gitStatus,
      onUpdate = repository::update,
      onCommitGitPath = controller::commit,
    )
  }
}

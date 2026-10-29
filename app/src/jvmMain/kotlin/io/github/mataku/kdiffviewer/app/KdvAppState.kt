package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.mataku.kdiffviewer.app.find.FindRequests
import io.github.mataku.kdiffviewer.app.find.FindState
import io.github.mataku.kdiffviewer.app.review.QuitWarning
import io.github.mataku.kdiffviewer.app.review.ReviewIntegration
import io.github.mataku.kdiffviewer.app.review.ReviewRequestHandler
import io.github.mataku.kdiffviewer.app.review.ReviewState
import io.github.mataku.kdiffviewer.app.review.SessionRegistry
import io.github.mataku.kdiffviewer.app.review.processAlive
import io.github.mataku.kdiffviewer.app.review.quitWarningOf
import io.github.mataku.kdiffviewer.app.settings.GitBackendController
import io.github.mataku.kdiffviewer.app.settings.SettingsRepository
import io.github.mataku.kdiffviewer.app.ui.ScrollJumps
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.time.Clock

@Stable
class KdvAppState(
  val viewer: DiffViewerState,
  val review: ReviewState,
  val gitBackend: GitBackendController,
  val integration: ReviewIntegration,
  private val exit: () -> Unit,
) {
  val scrollJumps = ScrollJumps()
  val findState = FindState()
  val findRequests = FindRequests()

  var quitWarning by mutableStateOf<QuitWarning?>(null)
    private set

  var quitPrompts by mutableIntStateOf(0)
    private set

  var settingsOpen by mutableStateOf(false)
    private set

  var settingsFocusRequests by mutableIntStateOf(0)
    private set

  var diffMode by mutableStateOf(DiffMode.Unified)
    private set

  var sidebarVisible by mutableStateOf(true)
    private set

  fun requestQuit() {
    val warning = quitWarning ?: quitWarningOf(review.snapshot.value)
    if (warning == null) {
      shutdown()
    } else {
      quitWarning = warning
      quitPrompts++
    }
  }

  fun cancelQuit() {
    quitWarning = null
  }

  fun shutdown() {
    integration.close()
    exit()
  }

  fun openSettings() {
    settingsOpen = true
    settingsFocusRequests++
  }

  fun closeSettings() {
    settingsOpen = false
  }

  fun openFolder() {
    pickFolder()?.let { viewer.open(RepoPath(it)) }
  }

  fun changeMode(mode: DiffMode) {
    if (mode != diffMode) diffMode = mode
  }

  fun toggleSidebar() {
    sidebarVisible = !sidebarVisible
  }
}

@Composable
fun rememberKdvAppState(
  args: Array<String>,
  settings: SettingsRepository,
  location: GitLocation,
  factory: GitBackendFactory,
  exit: () -> Unit,
): KdvAppState {
  val scope = remember { MainScope() }
  DisposableEffect(Unit) { onDispose { scope.cancel() } }
  val appState = remember {
    val viewer = createViewerState(args, location, factory, scope)
    val review = ReviewState(scope, Dispatchers.Default, Clock.System::now)
    KdvAppState(
      viewer = viewer,
      review = review,
      gitBackend = createGitBackendController(location, settings, factory, scope, viewer),
      integration = createIntegration(viewer, review),
      exit = exit,
    )
  }
  LaunchedEffect(appState) { appState.viewer.uiState.collect(appState.review::onViewerState) }
  LaunchedEffect(appState) {
    val integration = appState.integration
    val started = try {
      withContext(Dispatchers.IO) { integration.start() }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
    if (!started) return@LaunchedEffect
    integration.publishRoots(appState.viewer.uiState)
  }
  DisposableEffect(appState) { onDispose { appState.integration.close() } }
  return appState
}

private fun createViewerState(args: Array<String>, location: GitLocation, factory: GitBackendFactory, scope: CoroutineScope): DiffViewerState {
  val backend = (location as? GitLocation.Found)?.let { factory.create(it.info) }
  val missingSourceError = (location as? GitLocation.ConfiguredUnavailable)?.let { ViewerError.GitUnavailable(it.path) } ?: ViewerError.GitNotFound
  val viewer = DiffViewerState(
    source = backend?.source,
    scope = scope,
    highlightPipeline = backend?.highlightPipeline,
    missingSourceError = missingSourceError,
    loadContext = Dispatchers.Default,
    workingTree = backend?.workingTree,
  )
  when (val launch = parseLaunchArgs(args, System.getProperty("user.dir"))) {
    LaunchArgs.None -> Unit
    is LaunchArgs.Open -> viewer.open(launch.repo, launch.base)
    is LaunchArgs.Invalid -> viewer.fail(launch.repo, ViewerError.InvalidLaunchArgs(launch.message))
  }
  return viewer
}

private fun createGitBackendController(
  location: GitLocation,
  settings: SettingsRepository,
  factory: GitBackendFactory,
  scope: CoroutineScope,
  viewer: DiffViewerState,
) = GitBackendController(
  initial = location,
  settings = settings,
  scope = scope,
  ioDispatcher = Dispatchers.IO,
  probe = ::probeGit,
  locate = { GitLocator.locateOnHost(it) },
  createBackend = factory::create,
  onBackend = { viewer.setBackend(it?.source, it?.highlightPipeline, workingTree = it?.workingTree) },
)

private fun createIntegration(viewer: DiffViewerState, review: ReviewState): ReviewIntegration {
  val registry = SessionRegistry(
    Path.of(RegistryPaths.sessionsDir(System.getProperty("user.home"))),
    ProcessHandle.current().pid(),
    java.time.Instant::now,
    ::processAlive,
  )
  val handler = ReviewRequestHandler(
    review = review,
    reload = { withContext(Dispatchers.Main) { viewer.reload() } },
    info = {
      WindowInfoResult(ProcessHandle.current().pid(), registry.repoRoot, PROTOCOL_VERSION)
    },
  )
  return ReviewIntegration(registry, Dispatchers.IO, Dispatchers.IO, handler::handle)
}

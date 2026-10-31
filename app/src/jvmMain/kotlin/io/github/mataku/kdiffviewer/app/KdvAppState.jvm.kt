package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import io.github.mataku.kdiffviewer.app.review.ReviewIntegration
import io.github.mataku.kdiffviewer.app.review.ReviewRequestHandler
import io.github.mataku.kdiffviewer.app.review.ReviewState
import io.github.mataku.kdiffviewer.app.review.SessionRegistry
import io.github.mataku.kdiffviewer.app.review.processAlive
import io.github.mataku.kdiffviewer.app.settings.GitBackendController
import io.github.mataku.kdiffviewer.app.settings.SettingsRepository
import io.github.mataku.kdiffviewer.app.ui.ViewerScreenState
import io.github.mataku.kdiffviewer.core.git.GitLocation
import io.github.mataku.kdiffviewer.core.git.GitLocator
import io.github.mataku.kdiffviewer.core.git.locateOnHost
import io.github.mataku.kdiffviewer.core.git.probeGit
import io.github.mataku.kdiffviewer.protocol.HostOs
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

@Composable
fun rememberKdvAppState(
  args: Array<String>,
  repository: SettingsRepository,
  initialLocation: GitLocation,
  factory: GitBackendFactory,
  screen: ViewerScreenState,
  exit: () -> Unit,
): KdvAppState {
  val scope = remember { MainScope() }
  DisposableEffect(Unit) { onDispose { scope.cancel() } }
  val (appState, integration) = remember {
    val viewer = createViewerState(args, initialLocation, factory, scope)
    val review = ReviewState(scope, Dispatchers.Default, Clock.System::now)
    val integration = createIntegration(viewer, review)
    val appState = KdvAppState(
      viewer = viewer,
      review = review,
      gitBackend = createGitBackendController(initialLocation, repository, factory, scope, viewer),
      screen = screen,
      pickFolder = ::pickFolder,
      onShutdown = {
        integration.close()
        exit()
      },
    )
    appState to integration
  }
  LaunchedEffect(appState) { appState.viewer.uiState.collect(appState.review::onViewerState) }
  LaunchedEffect(integration) {
    val started = try {
      withContext(Dispatchers.IO) { integration.start() }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
    if (!started) return@LaunchedEffect
    integration.publishRoots(appState.viewer.uiState)
  }
  DisposableEffect(integration) { onDispose { integration.close() } }
  return appState
}

private fun createViewerState(args: Array<String>, initialLocation: GitLocation, factory: GitBackendFactory, scope: CoroutineScope): DiffViewerState {
  val backend = (initialLocation as? GitLocation.Found)?.let { factory.create(it.info) }
  val missingSourceError = (initialLocation as? GitLocation.ConfiguredUnavailable)?.let { ViewerError.GitUnavailable(it.path) } ?: ViewerError.GitNotFound
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
  initialLocation: GitLocation,
  repository: SettingsRepository,
  factory: GitBackendFactory,
  scope: CoroutineScope,
  viewer: DiffViewerState,
) = GitBackendController(
  initial = initialLocation,
  settings = repository,
  scope = scope,
  ioDispatcher = Dispatchers.IO,
  probe = ::probeGit,
  locate = { GitLocator.locateOnHost(it) },
  createBackend = factory::create,
  onBackend = { viewer.setBackend(it?.source, it?.highlightPipeline, workingTree = it?.workingTree) },
)

private fun hostOs(): HostOs = if (System.getProperty("os.name").startsWith("Mac")) HostOs.MacOs else HostOs.Linux

private fun createIntegration(viewer: DiffViewerState, review: ReviewState): ReviewIntegration {
  val registry = SessionRegistry(
    Path.of(RegistryPaths.sessionsDir(System.getProperty("user.home"), hostOs())),
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

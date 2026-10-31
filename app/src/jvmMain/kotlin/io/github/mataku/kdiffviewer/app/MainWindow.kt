package io.github.mataku.kdiffviewer.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import io.github.mataku.kdiffviewer.app.highlight.LocalCodeHighlighter
import io.github.mataku.kdiffviewer.app.review.messageLines
import io.github.mataku.kdiffviewer.app.selection.LocalNativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.selection.TextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.settings.Settings
import io.github.mataku.kdiffviewer.app.settings.SettingsRepository
import io.github.mataku.kdiffviewer.app.ui.AppShortcuts
import io.github.mataku.kdiffviewer.app.ui.ErrorScreen
import io.github.mataku.kdiffviewer.app.ui.KdvTheme
import io.github.mataku.kdiffviewer.app.ui.LocalAppShortcuts
import io.github.mataku.kdiffviewer.app.ui.LocalTitleBarRegions
import io.github.mataku.kdiffviewer.app.ui.OpenRepositoryScreen
import io.github.mataku.kdiffviewer.app.ui.ViewerScreen
import io.github.mataku.kdiffviewer.app.ui.ViewerTitleBarHeight
import io.github.mataku.kdiffviewer.app.ui.mac.MacAlertSheet
import io.github.mataku.kdiffviewer.app.ui.markdown.SafeUriHandler
import io.github.mataku.kdiffviewer.app.ui.resolveFontFamily
import io.github.mataku.kdiffviewer.app.ui.review.LocalReviewActions
import io.github.mataku.kdiffviewer.app.ui.scrollJumpOf
import java.awt.Desktop
import androidx.compose.ui.window.WindowState as ComposeWindowState

private val FallbackTrafficLightsWidth = 78.dp

@Composable
fun MainWindow(
  appState: KdvAppState,
  repository: SettingsRepository,
  settings: Settings,
  shortcuts: AppShortcuts,
  factory: GitBackendFactory,
  windowState: ComposeWindowState,
  nativePreferences: Boolean,
) {
  val viewer = appState.viewer
  val uiState by viewer.uiState.collectAsState()
  val reviewSnapshot by appState.review.snapshot.collectAsState()
  val currentShortcuts by rememberUpdatedState(shortcuts)
  Window(
    onCloseRequest = appState::requestQuit,
    title = "kDiffViewer",
    state = windowState,
    onKeyEvent = { event -> appState.quitWarning == null && (scrollJumpOf(event, currentShortcuts)?.let(appState.screen.scrollJumps::request) ?: false) },
  ) {
    LaunchedEffect(settings.appearance) { window.applyAppearance(settings.appearance) }
    LaunchedEffect(appState.quitPrompts) {
      if (appState.quitPrompts > 0) bringToFront(window, windowState)
    }
    val titleBar = remember { installCustomTitleBar(window, ViewerTitleBarHeight) }
    var titleBarLeadingInset by remember { mutableStateOf(0.dp) }
    LaunchedEffect(titleBar) {
      if (titleBar == null) return@LaunchedEffect
      withFrameNanos {}
      titleBarLeadingInset = titleBar.leftInset().takeIf { it > 0f }?.dp ?: FallbackTrafficLightsWidth
    }
    val coordinator = remember { TextSelectionCoordinator(AwtClipboardWriter) { repository.settings.value.copyOnSelect } }
    val nativeMenu = remember(window) { AwtNativeContextMenu(window) }
    AppMenuBar(appState, uiState, coordinator, nativePreferences, shortcuts)
    val fontFamily = remember(settings.fontFamily) { resolveFontFamily(settings.fontFamily) }
    val platformUriHandler = LocalUriHandler.current
    val safeUriHandler = remember(platformUriHandler) { SafeUriHandler(platformUriHandler) }
    CompositionLocalProvider(
      LocalTitleBarRegions provides titleBar?.regions,
      LocalReviewActions provides appState.review,
      LocalCodeHighlighter provides factory.codeHighlighter,
      LocalUriHandler provides safeUriHandler,
      LocalTextSelectionCoordinator provides coordinator,
      LocalNativeContextMenu provides nativeMenu,
      LocalAppShortcuts provides shortcuts,
    ) {
      KdvTheme(
        colorTheme = settings.appearance.colorTheme(),
        fontFamily = fontFamily,
        fontSize = settings.fontSize,
        tabWidth = settings.tabWidth,
        treeFontSize = settings.treeFontSize,
      ) {
        val status = uiState.status
        Box {
          when {
            uiState.repo == null -> OpenRepositoryScreen(onOpen = appState::openFolder)

            status is LoadStatus.Failed -> ErrorScreen(status.error, onOpenAnother = appState::openFolder, onOpenSettings = appState::openSettings)

            else -> ViewerScreen(
              uiState,
              onReload = viewer::reload,
              screenState = appState.screen,
              titleBarLeadingInset = titleBarLeadingInset,
              onToggleFile = viewer::toggleCollapsed,
              onExpandFile = viewer::expand,
              review = reviewSnapshot,
              loadBranches = viewer::branches,
              onSwitchBase = viewer::switchBase,
              onExpandContext = viewer::expandContext,
            )
          }
          appState.quitWarning?.let { warning ->
            MacAlertSheet(
              title = "Quit kDiffViewer?",
              messageLines = warning.messageLines(),
              confirmLabel = "Quit",
              onConfirm = appState::shutdown,
              onCancel = appState::cancelQuit,
            )
          }
        }
      }
    }
  }
}

private fun bringToFront(window: java.awt.Window, windowState: ComposeWindowState) {
  windowState.isMinimized = false
  val desktop = Desktop.getDesktop().takeIf { Desktop.isDesktopSupported() && it.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND) }
  desktop?.requestForeground(true)
  window.toFront()
}

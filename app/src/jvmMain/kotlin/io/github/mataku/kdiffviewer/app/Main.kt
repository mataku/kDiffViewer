package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.mataku.compose.highlight.api.Language
import io.github.mataku.compose.highlight.core.highlightLines
import io.github.mataku.kdiffviewer.app.highlight.HighlightPipeline
import io.github.mataku.kdiffviewer.app.highlight.LanguageResolver
import io.github.mataku.kdiffviewer.app.highlight.LineHighlighter
import io.github.mataku.kdiffviewer.app.highlight.defaultLanguageAccessors
import io.github.mataku.kdiffviewer.app.ui.ErrorScreen
import io.github.mataku.kdiffviewer.app.ui.KdvTheme
import io.github.mataku.kdiffviewer.app.ui.OpenRepositoryScreen
import io.github.mataku.kdiffviewer.app.ui.ViewerScreen
import io.github.mataku.kdiffviewer.core.git.FileSystemWorkingTreeReader
import io.github.mataku.kdiffviewer.core.git.GitContentSource
import io.github.mataku.kdiffviewer.core.git.GitDiffSource
import io.github.mataku.kdiffviewer.core.git.GitLocator
import io.github.mataku.kdiffviewer.core.git.ProcessGitBlobReader
import io.github.mataku.kdiffviewer.core.git.ProcessGitCommandRunner
import io.github.mataku.kdiffviewer.core.git.locateOnHost
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

private val MAC_TITLE_BAR_HEIGHT = 28.dp

fun main(args: Array<String>) {
  System.setProperty("apple.awt.application.appearance", "system")
  runApplication(args)
}

private fun runApplication(args: Array<String>) = application {
  val scope = remember { MainScope() }
  DisposableEffect(Unit) { onDispose { scope.cancel() } }
  val state = remember {
    val runner = GitLocator.locateOnHost()?.let(::ProcessGitCommandRunner)
    DiffViewerState(
      source = runner?.let { GitDiffSource(it, FileSystemWorkingTreeReader()) },
      scope = scope,
      highlightPipeline = if (runner != null && highlightEnabled()) highlightPipeline(runner) else null,
    ).also { viewerState ->
      parseLaunchArgs(args, System.getProperty("user.dir"))?.let(viewerState::open)
    }
  }
  val uiState by state.uiState.collectAsState()
  val openFolder: () -> Unit = { pickFolder()?.let { state.open(RepoPath(it)) } }

  Window(onCloseRequest = ::exitApplication, title = "kDiffViewer") {
    val titleBarInset = remember { if (mergeTitleBarIntoContent(window.rootPane)) MAC_TITLE_BAR_HEIGHT else 0.dp }
    MenuBar {
      Menu("File") {
        Item("Open…", shortcut = KeyShortcut(Key.O, meta = true), onClick = openFolder)
      }
      Menu("View") {
        Item("Reload", shortcut = KeyShortcut(Key.R, meta = true), onClick = state::reload)
      }
    }
    KdvTheme {
      val status = uiState.status
      when {
        uiState.repo == null -> OpenRepositoryScreen(onOpen = openFolder)
        status is LoadStatus.Failed -> ErrorScreen(status.error, onOpenAnother = openFolder)
        else -> ViewerScreen(uiState, onReload = state::reload, titleBarInset = titleBarInset)
      }
    }
  }
}

private fun highlightEnabled(): Boolean = System.getProperty("kdv.highlight") != "false"

private fun highlightPipeline(runner: ProcessGitCommandRunner): HighlightPipeline<Language> = HighlightPipeline(
  contentSource = GitContentSource(runner, { root -> ProcessGitBlobReader(runner.gitExecutable, root) }, FileSystemWorkingTreeReader()),
  languages = LanguageResolver(defaultLanguageAccessors),
  highlighter = LineHighlighter(::highlightLines),
  dispatcher = Dispatchers.Default,
)

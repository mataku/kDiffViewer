package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.mataku.kdiffviewer.app.ui.ErrorScreen
import io.github.mataku.kdiffviewer.app.ui.OpenRepositoryScreen
import io.github.mataku.kdiffviewer.app.ui.ViewerScreen
import io.github.mataku.kdiffviewer.core.git.FileSystemWorkingTreeReader
import io.github.mataku.kdiffviewer.core.git.GitDiffSource
import io.github.mataku.kdiffviewer.core.git.GitLocator
import io.github.mataku.kdiffviewer.core.git.ProcessGitCommandRunner
import io.github.mataku.kdiffviewer.core.git.locateOnHost
import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

fun main(args: Array<String>) = application {
    val scope = remember { MainScope() }
    DisposableEffect(Unit) { onDispose { scope.cancel() } }
    val state = remember {
        DiffViewerState(
            source = GitLocator.locateOnHost()?.let { GitDiffSource(ProcessGitCommandRunner(it), FileSystemWorkingTreeReader()) },
            scope = scope,
        ).also { viewerState ->
            parseLaunchArgs(args, System.getProperty("user.dir"))?.let(viewerState::open)
        }
    }
    val uiState by state.uiState.collectAsState()
    val openFolder: () -> Unit = { pickFolder()?.let { state.open(RepoPath(it)) } }

    Window(onCloseRequest = ::exitApplication, title = "kDiffViewer") {
        MenuBar {
            Menu("File") {
                Item("Open…", shortcut = KeyShortcut(Key.O, meta = true), onClick = openFolder)
            }
            Menu("View") {
                Item("Reload", shortcut = KeyShortcut(Key.R, meta = true), onClick = state::reload)
            }
        }
        val status = uiState.status
        when {
            uiState.repo == null -> OpenRepositoryScreen(onOpen = openFolder)
            status is LoadStatus.Failed -> ErrorScreen(status.error, onOpenAnother = openFolder)
            else -> ViewerScreen(uiState, onReload = state::reload)
        }
    }
}

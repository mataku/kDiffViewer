package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import io.github.mataku.kdiffviewer.app.find.FindCommand
import io.github.mataku.kdiffviewer.app.selection.TextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.ui.AppShortcuts
import io.github.mataku.kdiffviewer.app.ui.Chord

@Composable
fun FrameWindowScope.AppMenuBar(
  appState: KdvAppState,
  uiState: ViewerUiState,
  coordinator: TextSelectionCoordinator,
  nativePreferences: Boolean,
) {
  val viewerAvailable = uiState.repo != null && uiState.status !is LoadStatus.Failed
  val screen = appState.screen
  val shortcuts = AppShortcuts.Host
  MenuBar {
    Menu("File") {
      Item("Open…", shortcut = shortcuts.open.toKeyShortcut(), onClick = appState::openFolder)
      if (!nativePreferences) {
        Item("Settings…", shortcut = shortcuts.settings.toKeyShortcut(), onClick = appState::openSettings)
      }
    }
    Menu("Edit") {
      Item("Copy", shortcut = shortcuts.copy.toKeyShortcut(), enabled = coordinator.hasSelection, onClick = { coordinator.copy() })
      Separator()
      Menu("Find") {
        val requests = screen.findRequests
        Item("Find…", shortcut = shortcuts.find.toKeyShortcut(), enabled = viewerAvailable, onClick = { requests.request(FindCommand.Show) })
        Item("Find Next", shortcut = shortcuts.findNext.toKeyShortcut(), enabled = viewerAvailable, onClick = { requests.request(FindCommand.Next) })
        Item("Find Previous", shortcut = shortcuts.findPrevious.toKeyShortcut(), enabled = viewerAvailable, onClick = { requests.request(FindCommand.Previous) })
      }
    }
    Menu("View") {
      RadioButtonItem("Unified", selected = screen.mode == DiffMode.Unified, onClick = { screen.changeMode(DiffMode.Unified) })
      RadioButtonItem("Split", selected = screen.mode == DiffMode.Split, onClick = { screen.changeMode(DiffMode.Split) })
      Separator()
      Item(
        if (screen.sidebarVisible) "Hide Sidebar" else "Show Sidebar",
        shortcut = shortcuts.toggleSidebar.toKeyShortcut(),
        enabled = viewerAvailable,
        onClick = screen::toggleSidebar,
      )
      Separator()
      Item("Reload", shortcut = shortcuts.reload.toKeyShortcut(), onClick = appState.viewer::reload)
    }
  }
}

private fun Chord.toKeyShortcut(): KeyShortcut = KeyShortcut(key, ctrl = ctrl, meta = meta, shift = shift)

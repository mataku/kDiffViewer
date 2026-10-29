package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import io.github.mataku.kdiffviewer.app.find.FindCommand
import io.github.mataku.kdiffviewer.app.selection.TextSelectionCoordinator

@Composable
fun FrameWindowScope.AppMenuBar(
  appState: KdvAppState,
  uiState: ViewerUiState,
  coordinator: TextSelectionCoordinator,
  nativePreferences: Boolean,
) {
  val viewerAvailable = uiState.repo != null && uiState.status !is LoadStatus.Failed
  MenuBar {
    Menu("File") {
      Item("Open…", shortcut = KeyShortcut(Key.O, meta = true), onClick = appState::openFolder)
      if (!nativePreferences) {
        Item("Settings…", shortcut = KeyShortcut(Key.Comma, meta = true), onClick = appState::openSettings)
      }
    }
    Menu("Edit") {
      Item("Copy", shortcut = KeyShortcut(Key.C, meta = true), enabled = coordinator.hasSelection, onClick = { coordinator.copy() })
      Separator()
      Menu("Find") {
        val requests = appState.findRequests
        Item("Find…", shortcut = KeyShortcut(Key.F, meta = true), enabled = viewerAvailable, onClick = { requests.request(FindCommand.Show) })
        Item("Find Next", shortcut = KeyShortcut(Key.G, meta = true), enabled = viewerAvailable, onClick = { requests.request(FindCommand.Next) })
        Item("Find Previous", shortcut = KeyShortcut(Key.G, meta = true, shift = true), enabled = viewerAvailable, onClick = { requests.request(FindCommand.Previous) })
      }
    }
    Menu("View") {
      RadioButtonItem("Unified", selected = appState.diffMode == DiffMode.Unified, onClick = { appState.changeMode(DiffMode.Unified) })
      RadioButtonItem("Split", selected = appState.diffMode == DiffMode.Split, onClick = { appState.changeMode(DiffMode.Split) })
      Separator()
      Item(
        if (appState.sidebarVisible) "Hide Sidebar" else "Show Sidebar",
        shortcut = KeyShortcut(Key.S, ctrl = true, meta = true),
        enabled = viewerAvailable,
        onClick = appState::toggleSidebar,
      )
      Separator()
      Item("Reload", shortcut = KeyShortcut(Key.R, meta = true), onClick = { appState.viewer.reload() })
    }
  }
}

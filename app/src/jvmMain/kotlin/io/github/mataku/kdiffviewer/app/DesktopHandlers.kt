package io.github.mataku.kdiffviewer.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.Desktop

@Composable
fun rememberNativePreferencesSupported(): Boolean = remember {
  Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_PREFERENCES)
}

@Composable
fun DesktopHandlersEffect(nativePreferences: Boolean, onPreferences: () -> Unit, onQuit: () -> Unit) {
  val currentOnPreferences by rememberUpdatedState(onPreferences)
  val currentOnQuit by rememberUpdatedState(onQuit)
  if (nativePreferences) {
    DisposableEffect(Unit) {
      Desktop.getDesktop().setPreferencesHandler { currentOnPreferences() }
      onDispose { Desktop.getDesktop().setPreferencesHandler(null) }
    }
  }
  DisposableEffect(Unit) {
    val desktop = Desktop.getDesktop().takeIf { Desktop.isDesktopSupported() && it.isSupported(Desktop.Action.APP_QUIT_HANDLER) }
    desktop?.setQuitHandler { _, response ->
      response.cancelQuit()
      currentOnQuit()
    }
    onDispose { desktop?.setQuitHandler(null) }
  }
}

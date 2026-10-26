package io.github.mataku.kdiffviewer.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeWindow
import io.github.mataku.kdiffviewer.app.settings.Appearance

private const val WINDOW_APPEARANCE_PROPERTY = "apple.awt.windowAppearance"

fun ComposeWindow.applyAppearance(appearance: Appearance) {
  val value = when (appearance) {
    Appearance.Light -> "NSAppearanceNameAqua"
    Appearance.Dark -> "NSAppearanceNameDarkAqua"
    Appearance.System -> null
  }
  rootPane.putClientProperty(WINDOW_APPEARANCE_PROPERTY, value)
}

@Composable
fun Appearance.isDark(): Boolean = when (this) {
  Appearance.Light -> false
  Appearance.Dark -> true
  Appearance.System -> isSystemInDarkTheme()
}

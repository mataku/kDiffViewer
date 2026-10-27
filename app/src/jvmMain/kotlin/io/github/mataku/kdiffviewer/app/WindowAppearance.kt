package io.github.mataku.kdiffviewer.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeWindow
import io.github.mataku.kdiffviewer.app.settings.Appearance
import io.github.mataku.kdiffviewer.app.ui.ColorTheme

private const val WINDOW_APPEARANCE_PROPERTY = "apple.awt.windowAppearance"

fun ComposeWindow.applyAppearance(appearance: Appearance) {
  val value = when (appearance) {
    Appearance.Light -> "NSAppearanceNameAqua"
    Appearance.Dark, Appearance.Dim -> "NSAppearanceNameDarkAqua"
    Appearance.System -> null
  }
  rootPane.putClientProperty(WINDOW_APPEARANCE_PROPERTY, value)
}

@Composable
fun Appearance.colorTheme(): ColorTheme = when (this) {
  Appearance.Light -> ColorTheme.Light
  Appearance.Dark -> ColorTheme.Dark
  Appearance.Dim -> ColorTheme.Dim
  Appearance.System -> if (isSystemInDarkTheme()) ColorTheme.Dark else ColorTheme.Light
}

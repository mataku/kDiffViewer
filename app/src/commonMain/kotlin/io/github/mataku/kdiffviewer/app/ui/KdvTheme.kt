package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.mataku.compose.highlight.core.LocalSyntaxTheme

@Composable
fun KdvTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val diffColors = if (darkTheme) DarkDiffColors else LightDiffColors
  val syntaxTheme = if (darkTheme) DarkSyntaxTheme else LightSyntaxTheme
  val base = if (darkTheme) darkColorScheme() else lightColorScheme()
  MaterialTheme(colorScheme = base.withTokens(diffColors)) {
    CompositionLocalProvider(
      LocalDiffColors provides diffColors,
      LocalSyntaxTheme provides syntaxTheme,
      content = content,
    )
  }
}

private fun ColorScheme.withTokens(colors: DiffColors): ColorScheme = copy(
  background = colors.background,
  onBackground = colors.text,
  surface = colors.background,
  onSurface = colors.text,
  surfaceVariant = colors.fileHeaderBackground,
  onSurfaceVariant = colors.mutedText,
  outline = colors.border,
)

package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import io.github.mataku.compose.highlight.core.LocalSyntaxTheme
import io.github.mataku.kdiffviewer.app.ui.markdown.LocalMarkdownStyles
import io.github.mataku.kdiffviewer.app.ui.markdown.MarkdownStyles

@Composable
fun KdvTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  fontFamily: FontFamily = FontFamily.Monospace,
  fontSize: Int = 12,
  tabWidth: Int = 8,
  treeFontSize: Int = 13,
  content: @Composable () -> Unit,
) {
  val diffColors = if (darkTheme) DarkDiffColors else LightDiffColors
  val typography = remember(fontFamily, fontSize, tabWidth, diffColors) {
    diffTypography(fontFamily, fontSize, tabWidth, diffColors)
  }
  val treeTypography = remember(treeFontSize, diffColors) { treeTypography(treeFontSize, diffColors) }
  val markdownStyles = remember(diffColors, typography) { MarkdownStyles(diffColors, typography) }
  val syntaxTheme = if (darkTheme) DarkSyntaxTheme else LightSyntaxTheme
  val base = if (darkTheme) darkColorScheme() else lightColorScheme()
  MaterialTheme(colorScheme = base.withTokens(diffColors)) {
    CompositionLocalProvider(
      LocalDiffColors provides diffColors,
      LocalDiffTypography provides typography,
      LocalTreeTypography provides treeTypography,
      LocalMarkdownStyles provides markdownStyles,
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

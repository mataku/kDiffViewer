package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.text.SpanStyle
import io.github.mataku.compose.highlight.core.SyntaxTheme

val LightSyntaxTheme: SyntaxTheme = SyntaxTheme.GitHubLight.copy(baseStyle = SpanStyle())

val DarkSyntaxTheme: SyntaxTheme = SyntaxTheme.GitHubDark.copy(baseStyle = SpanStyle())

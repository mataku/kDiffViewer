package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.ViewerError

@Composable
fun OpenRepositoryScreen(onOpen: () -> Unit) {
    MessageLayout {
        ActionButton("Open Repository…", onOpen)
    }
}

@Composable
fun ErrorScreen(error: ViewerError, onOpenAnother: () -> Unit) {
    MessageLayout {
        val colors = LocalDiffColors.current
        val message = when (error) {
            ViewerError.GitNotFound -> "git was not found. Install git and restart kDiffViewer."
            is ViewerError.NotARepository -> "${error.path} is not a git repository."
            is ViewerError.GitFailed -> "git failed."
        }
        BasicText(text = message, style = TextStyle(color = colors.removedText, fontSize = 15.sp))
        if (error is ViewerError.GitFailed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .border(1.dp, colors.border)
                    .background(colors.fileHeaderBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                BasicText(
                    text = error.details(),
                    style = TextStyle(color = colors.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                )
            }
        }
        ActionButton("Open Another Folder…", onOpenAnother)
    }
}

@Composable
private fun MessageLayout(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDiffColors provides LightDiffColors) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(32.dp).widthIn(max = 640.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    val colors = LocalDiffColors.current
    BasicText(
        text = label,
        style = TextStyle(color = colors.text, fontSize = 14.sp),
        modifier = Modifier
            .border(1.dp, colors.border)
            .background(colors.fileHeaderBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun ViewerError.GitFailed.details(): String = when {
    stderr.isNotBlank() -> stderr
    exitCode != null -> "git exited with code $exitCode"
    else -> stderr
}

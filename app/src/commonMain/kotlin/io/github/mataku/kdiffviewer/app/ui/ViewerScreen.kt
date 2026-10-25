package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.LoadStatus
import io.github.mataku.kdiffviewer.app.ScrollRestorer
import io.github.mataku.kdiffviewer.app.ViewerUiState
import kotlinx.coroutines.launch

@Composable
fun ViewerScreen(
    state: ViewerUiState,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalDiffColors provides LightDiffColors) {
        val colors = LocalDiffColors.current
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val currentState by rememberUpdatedState(state)
        val restorer = remember { ScrollRestorer() }
        LaunchedEffect(listState) {
            snapshotFlow { currentState to listState.firstVisibleItemIndex }.collect { (ui, firstVisible) ->
                restorer.next(ui, firstVisible)?.let { listState.scrollToItem(it) }
            }
        }
        Column(modifier = modifier.fillMaxSize().background(Color.White)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.fileHeaderBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(
                    text = state.repo?.value.orEmpty(),
                    style = TextStyle(color = colors.text, fontSize = 13.sp),
                )
                BasicText(
                    text = "Reload",
                    modifier = Modifier
                        .testTag("reload-button")
                        .border(1.dp, colors.border)
                        .clickable(onClick = onReload)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = TextStyle(color = colors.text, fontSize = 13.sp),
                )
            }
            if (state.status == LoadStatus.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.fillMaxSize()) {
                FileTreePanel(
                    tree = state.tree,
                    onFileClick = { fileIndex ->
                        scope.launch { listState.scrollToItem(state.fileHeaderRowIndex[fileIndex]) }
                    },
                )
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (state.status == LoadStatus.Done && state.files.isEmpty()) {
                        BasicText(
                            text = "No changes",
                            modifier = Modifier.align(Alignment.Center),
                            style = TextStyle(color = colors.mutedText, fontSize = 14.sp),
                        )
                    } else {
                        DiffView(rows = state.rows, listState = listState)
                    }
                }
            }
        }
    }
}

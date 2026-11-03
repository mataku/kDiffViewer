package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import io.github.mataku.kdiffviewer.core.review.ReviewThread

private val ConversationPanelMaxHeight = 320.dp

@Composable
fun ConversationPanel(threads: List<ReviewThread>, draftTarget: DraftTarget?, modifier: Modifier = Modifier) {
  val colors = LocalDiffColors.current
  val scroll = rememberScrollState()
  Column(
    modifier = modifier
      .testTag("conversation-panel")
      .fillMaxWidth()
      .heightIn(max = ConversationPanelMaxHeight)
      .background(colors.background)
      .verticalScroll(scroll)
      .padding(horizontal = 20.dp),
  ) {
    NoteStack(threads, draftTarget, composeNew = false)
  }
}

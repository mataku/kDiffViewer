package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.review.DraftMode
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import io.github.mataku.kdiffviewer.core.review.ReviewThread

@Composable
fun NoteStack(threads: List<ReviewThread>, draftTarget: DraftTarget?, composeNew: Boolean, modifier: Modifier = Modifier) {
  val actions = LocalReviewActions.current
  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    threads.forEach { thread ->
      key(thread.id) { ThreadCard(thread, if (draftTarget.touches(thread)) draftTarget else null) }
    }
    if (composeNew && draftTarget != null && actions != null) {
      CommentComposer(
        submitLabel = "Comment",
        onTextChange = actions::updateDraft,
        onSubmit = { actions.submitDraft(DraftMode.Single) },
        onCancel = actions::cancelDraft,
      )
    }
  }
}

@Composable
fun OrphanSection(threads: List<ReviewThread>, draftTarget: DraftTarget?, composeNew: Boolean, modifier: Modifier = Modifier) {
  val colors = LocalDiffColors.current
  val style = remember(colors) { TextStyle(color = colors.mutedText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
  Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
    BasicText(text = "Comments on files no longer in the diff", style = style)
    NoteStack(threads, draftTarget, composeNew)
  }
}

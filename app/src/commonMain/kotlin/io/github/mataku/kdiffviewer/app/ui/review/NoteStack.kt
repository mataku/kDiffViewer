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
import androidx.compose.ui.platform.testTag
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
        actions = ComposerActions.Post(
          onReview = { actions.submitDraft(DraftMode.Review) },
          onSingle = { actions.submitDraft(DraftMode.Single) },
        ),
        onTextChange = actions::updateDraft,
        onCancel = actions::cancelDraft,
      )
    }
  }
}

@Composable
fun TopSection(branch: List<ReviewThread>, orphans: List<ReviewThread>, draftTarget: DraftTarget?, composeOrphan: Boolean, modifier: Modifier = Modifier) {
  val colors = LocalDiffColors.current
  val style = remember(colors) { TextStyle(color = colors.mutedText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
  Column(modifier = modifier.fillMaxWidth().testTag("top-section")) {
    if (branch.isNotEmpty()) {
      BasicText(text = "Conversation", modifier = Modifier.padding(top = 12.dp), style = style)
      NoteStack(branch, draftTarget, composeNew = false)
    }
    if (orphans.isNotEmpty() || composeOrphan) {
      BasicText(text = "Comments on files no longer in the diff", modifier = Modifier.padding(top = 12.dp), style = style)
      NoteStack(orphans, draftTarget, composeOrphan)
    }
  }
}

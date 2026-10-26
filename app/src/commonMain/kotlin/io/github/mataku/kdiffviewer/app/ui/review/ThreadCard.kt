package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.review.Draft
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import io.github.mataku.kdiffviewer.app.ui.LocalDiffTypography
import io.github.mataku.kdiffviewer.app.ui.MacPushButton
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.ReviewThread

private val CardShape = RoundedCornerShape(6.dp)

@Composable
fun ThreadCard(thread: ReviewThread, draft: Draft?) {
  val colors = LocalDiffColors.current
  var expanded by remember(thread.id) { mutableStateOf(false) }
  val collapsed = thread.resolved && !expanded
  val cardModifier = Modifier
    .fillMaxWidth()
    .background(colors.threadBackground, CardShape)
    .border(0.5.dp, colors.threadBorder, CardShape)
  if (collapsed) {
    val firstLine = thread.comments.firstOrNull()?.body?.lineSequence()?.firstOrNull().orEmpty()
    BasicText(
      text = "Resolved · $firstLine",
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = TextStyle(color = colors.mutedText, fontSize = 12.sp),
      modifier = cardModifier
        .testTag("thread-${thread.id}-collapsed")
        .clickable { expanded = true }
        .padding(horizontal = 12.dp, vertical = 6.dp),
    )
    return
  }
  val actions = LocalReviewActions.current
  Column(
    modifier = cardModifier.testTag("thread-${thread.id}").padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (thread.outdated) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Badge("Outdated", colors.outdatedBadge)
        val snapshot = (thread.anchor as? Anchor.Lines)?.snapshot.orEmpty()
        val code = LocalDiffTypography.current.code
        snapshot.forEach { line -> BasicText(text = line, style = code) }
      }
    }
    thread.comments.forEach { comment ->
      CommentView(comment, actions)
    }
    val draftTarget = draft?.target
    if (draftTarget is DraftTarget.Reply && draftTarget.threadId == thread.id && actions != null) {
      CommentComposer(
        text = draft.text,
        submitLabel = "Reply",
        onTextChange = actions::updateDraft,
        onSubmit = actions::submitDraft,
        onCancel = actions::cancelDraft,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      MacPushButton("Reply", onClick = { actions?.startDraft(DraftTarget.Reply(thread.id)) })
      if (thread.resolved) {
        MacPushButton("Unresolve", onClick = { actions?.setResolved(thread.id, false) })
      } else {
        MacPushButton("Resolve", onClick = { actions?.setResolved(thread.id, true) })
      }
    }
  }
}

@Composable
private fun CommentView(comment: Comment, actions: ReviewActions?) {
  val colors = LocalDiffColors.current
  val editing = (comment.author == Author.User)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      when (val author = comment.author) {
        Author.User -> BasicText("You", style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))

        is Author.Agent -> {
          BasicText(author.name, style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
          Badge("Agent", colors.agentBadge)
        }
      }
      if (editing) {
        LinkText("Edit", colors.accent) { actions?.startDraft(DraftTarget.Edit(comment.id)) }
        LinkText("Delete", colors.accent) { actions?.delete(comment.id) }
      }
    }
    BasicText(comment.body, style = TextStyle(color = colors.text, fontSize = 13.sp))
  }
}

@Composable
private fun LinkText(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
  BasicText(text, style = TextStyle(color = color, fontSize = 12.sp), modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
  BasicText(
    text = text,
    style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    modifier = Modifier
      .border(0.5.dp, color, RoundedCornerShape(10.dp))
      .padding(horizontal = 6.dp, vertical = 1.dp),
  )
}

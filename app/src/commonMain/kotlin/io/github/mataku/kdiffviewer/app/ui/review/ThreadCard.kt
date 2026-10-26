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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.ui.DiffColors
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import io.github.mataku.kdiffviewer.app.ui.LocalDiffTypography
import io.github.mataku.kdiffviewer.app.ui.MacPushButton
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.ReviewThread

private val CardShape = RoundedCornerShape(6.dp)

@Immutable
private class CardStyles(colors: DiffColors) {
  val collapsed = TextStyle(color = colors.mutedText, fontSize = 12.sp)
  val author = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
  val body = TextStyle(color = colors.text, fontSize = 13.sp)
  val link = TextStyle(color = colors.accent, fontSize = 12.sp)
  fun badge(color: Color) = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
  val agentBadge = badge(colors.agentBadge)
  val outdatedBadge = badge(colors.outdatedBadge)
}

@Composable
fun ThreadCard(thread: ReviewThread, draftTarget: DraftTarget?) {
  val colors = LocalDiffColors.current
  val styles = remember(colors) { CardStyles(colors) }
  var expanded by remember(thread.id) { mutableStateOf(false) }
  val collapsed = thread.resolved && !expanded && !draftTarget.touches(thread)
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
      style = styles.collapsed,
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
        Badge("Outdated", colors.outdatedBadge, styles.outdatedBadge)
        val snapshot = (thread.anchor as? Anchor.Lines)?.snapshot.orEmpty()
        val code = LocalDiffTypography.current.code
        snapshot.forEach { line -> BasicText(text = line, style = code) }
      }
    }
    thread.comments.forEach { comment ->
      val editing = draftTarget is DraftTarget.Edit && draftTarget.commentId == comment.id
      CommentView(comment, editing, actions, styles, colors)
    }
    if (draftTarget is DraftTarget.Reply && draftTarget.threadId == thread.id && actions != null) {
      CommentComposer(
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

internal fun DraftTarget?.touches(thread: ReviewThread): Boolean = when (val target = this) {
  is DraftTarget.Reply -> target.threadId == thread.id
  is DraftTarget.Edit -> thread.comments.any { it.id == target.commentId }
  else -> false
}

@Composable
private fun CommentView(comment: Comment, editing: Boolean, actions: ReviewActions?, styles: CardStyles, colors: DiffColors) {
  val isOwn = comment.author == Author.User
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      when (val author = comment.author) {
        Author.User -> BasicText("You", style = styles.author)

        is Author.Agent -> {
          BasicText(author.name, style = styles.author)
          Badge("Agent", colors.agentBadge, styles.agentBadge)
        }
      }
      if (isOwn) {
        LinkText("Edit", styles.link) { actions?.startDraft(DraftTarget.Edit(comment.id)) }
        LinkText("Delete", styles.link) { actions?.delete(comment.id) }
      }
    }
    if (editing && actions != null) {
      CommentComposer(
        submitLabel = "Save",
        onTextChange = actions::updateDraft,
        onSubmit = actions::submitDraft,
        onCancel = actions::cancelDraft,
      )
    } else {
      BasicText(comment.body, style = styles.body)
    }
  }
}

@Composable
private fun LinkText(text: String, style: TextStyle, onClick: () -> Unit) {
  BasicText(text, style = style, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun Badge(text: String, color: Color, style: TextStyle) {
  BasicText(
    text = text,
    style = style,
    modifier = Modifier
      .border(0.5.dp, color, RoundedCornerShape(10.dp))
      .padding(horizontal = 6.dp, vertical = 1.dp),
  )
}

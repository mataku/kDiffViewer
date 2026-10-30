package io.github.mataku.kdiffviewer.app.ui.review

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mataku.kdiffviewer.app.review.DraftMode
import io.github.mataku.kdiffviewer.app.review.DraftTarget
import io.github.mataku.kdiffviewer.app.selection.LocalNativeContextMenu
import io.github.mataku.kdiffviewer.app.selection.LocalTextSelectionCoordinator
import io.github.mataku.kdiffviewer.app.ui.DiffColors
import io.github.mataku.kdiffviewer.app.ui.LocalDiffColors
import io.github.mataku.kdiffviewer.app.ui.LocalDiffTypography
import io.github.mataku.kdiffviewer.app.ui.mac.MacPushButton
import io.github.mataku.kdiffviewer.app.ui.markdown.MarkdownBody
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Author
import io.github.mataku.kdiffviewer.core.review.Comment
import io.github.mataku.kdiffviewer.core.review.ReviewThread
import io.github.mataku.kdiffviewer.core.review.isPending
import io.github.mataku.kdiffviewer.protocol.markControls

private val CardShape = RoundedCornerShape(6.dp)

@Immutable
private class CardStyles(colors: DiffColors) {
  val collapsed = TextStyle(color = colors.mutedText, fontSize = 12.sp)
  val author = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
  val link = TextStyle(color = colors.accent, fontSize = 12.sp)
  fun badge(color: Color) = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
  val agentBadge = badge(colors.agentBadge)
  val outdatedBadge = badge(colors.outdatedBadge)
}

@OptIn(ExperimentalFoundationApi::class)
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
        .clickable(interactionSource = null, indication = null) { expanded = true }
        .padding(horizontal = 12.dp, vertical = 6.dp),
    )
    return
  }
  val actions = LocalReviewActions.current
  val selectionState = remember(thread.id) { SelectionState() }
  val source = remember(selectionState) { CardSelectionSource(selectionState) }
  val coordinator = LocalTextSelectionCoordinator.current
  DisposableEffect(source, coordinator) { onDispose { coordinator?.release(source) } }
  val nativeMenu = LocalNativeContextMenu.current
  val density = LocalDensity.current
  val textContextMenu = remember(nativeMenu, density) {
    nativeMenu?.let { NativeTextContextMenu(it, density) }
  }
  CompositionLocalProvider(
    *listOfNotNull(textContextMenu?.let { LocalTextContextMenu provides it }).toTypedArray(),
  ) {
    SelectionContainer(
      state = selectionState,
      modifier = cardModifier
        .testTag("thread-${thread.id}")
        .pointerInput(source, coordinator) {
          awaitPointerEventScope {
            var primaryGesture = false
            var textsAtPress: List<AnnotatedString> = emptyList()
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              when (event.type) {
                PointerEventType.Press -> {
                  primaryGesture = event.buttons.isPrimaryPressed && !event.keyboardModifiers.isCtrlPressed
                  if (primaryGesture) {
                    coordinator?.activate(source)
                    textsAtPress = selectionState.selectedTexts.toList()
                  }
                }

                PointerEventType.Release -> if (primaryGesture && event.buttons.isPrimaryPressed.not()) {
                  primaryGesture = false
                  if (source.hasSelection && selectionState.selectedTexts != textsAtPress) coordinator?.onSelectionFinished(source)
                }
              }
            }
          }
        }
        .padding(12.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (thread.outdated) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DisableSelection { Badge("Outdated", colors.outdatedBadge, styles.outdatedBadge) }
            val snapshot = (thread.anchor as? Anchor.Lines)?.snapshot.orEmpty()
            val code = LocalDiffTypography.current.code
            snapshot.forEach { line -> BasicText(text = markControls(line), style = code) }
          }
        }
        thread.comments.forEach { comment ->
          val editing = draftTarget is DraftTarget.Edit && draftTarget.commentId == comment.id
          CommentView(comment, editing, actions, styles, colors)
        }
        DisableSelection {
          if (draftTarget is DraftTarget.Reply && draftTarget.threadId == thread.id && actions != null) {
            CommentComposer(
              actions = ComposerActions.Post(
                onReview = { actions.submitDraft(DraftMode.Review) },
                onSingle = if (thread.isPending) null else ({ actions.submitDraft(DraftMode.Single) }),
              ),
              onTextChange = actions::updateDraft,
              onCancel = actions::cancelDraft,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacPushButton("Reply", onClick = { actions?.startDraft(DraftTarget.Reply(thread.id)) })
            when {
              thread.isPending -> Unit
              thread.resolved -> MacPushButton("Unresolve", onClick = { actions?.setResolved(thread.id, false) })
              else -> MacPushButton("Resolve", onClick = { actions?.setResolved(thread.id, true) })
            }
          }
        }
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
    DisableSelection {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when (val author = comment.author) {
          Author.User -> BasicText("You", style = styles.author)

          is Author.Agent -> {
            BasicText(author.name, style = styles.author)
            Badge("Agent", colors.agentBadge, styles.agentBadge)
          }
        }
        if (comment.pending) Badge("Pending", colors.outdatedBadge, styles.outdatedBadge)
        if (isOwn) {
          var confirmingDelete by remember(comment.id) { mutableStateOf(false) }
          if (confirmingDelete) {
            BasicText("Delete?", style = styles.collapsed)
            LinkText("Delete", styles.link) { actions?.delete(comment.id) }
            LinkText("Cancel", styles.link) { confirmingDelete = false }
          } else {
            LinkText("Edit", styles.link) { actions?.startDraft(DraftTarget.Edit(comment.id)) }
            LinkText("Delete", styles.link) { confirmingDelete = true }
          }
        }
      }
    }
    if (editing && actions != null) {
      DisableSelection {
        CommentComposer(
          actions = ComposerActions.Save { actions.submitDraft(DraftMode.Single) },
          onTextChange = actions::updateDraft,
          onCancel = actions::cancelDraft,
        )
      }
    } else {
      MarkdownBody(comment.body)
    }
  }
}

@Composable
private fun LinkText(text: String, style: TextStyle, onClick: () -> Unit) {
  BasicText(text, style = style, modifier = Modifier.clickable(interactionSource = null, indication = null, onClick = onClick))
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

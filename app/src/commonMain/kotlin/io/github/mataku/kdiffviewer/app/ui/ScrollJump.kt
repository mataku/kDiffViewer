package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ScrollJump { Top, Bottom }

fun scrollJumpOf(event: KeyEvent, shortcuts: KeyboardShortcuts = KeyboardShortcuts.Host): ScrollJump? = when {
  shortcuts.scrollToTop.matches(event) -> ScrollJump.Top
  shortcuts.scrollToBottom.matches(event) -> ScrollJump.Bottom
  else -> null
}

class ScrollJumps {
  private val flow = MutableSharedFlow<ScrollJump>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val requests: SharedFlow<ScrollJump> = flow.asSharedFlow()

  fun request(jump: ScrollJump): Boolean = flow.tryEmit(jump)
}

fun Modifier.keepScrollJumps(shortcuts: KeyboardShortcuts): Modifier = onKeyEvent { event -> scrollJumpOf(event, shortcuts) != null }

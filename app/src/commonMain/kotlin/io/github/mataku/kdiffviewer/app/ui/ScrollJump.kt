package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ScrollJump { Top, Bottom }

fun scrollJumpOf(event: KeyEvent, keymap: Keymap = HostKeymap): ScrollJump? {
  if (event.type != KeyEventType.KeyDown || event.isShiftPressed || event.isAltPressed) return null
  val meta = event.isMetaPressed
  val ctrl = event.isCtrlPressed
  val mac = keymap == Keymap.Mac
  return when {
    mac && meta && !ctrl && event.key == Key.DirectionUp -> ScrollJump.Top
    mac && meta && !ctrl && event.key == Key.DirectionDown -> ScrollJump.Bottom
    ctrl && !meta && event.key == Key.MoveHome -> ScrollJump.Top
    ctrl && !meta && event.key == Key.MoveEnd -> ScrollJump.Bottom
    else -> null
  }
}

class ScrollJumps {
  private val flow = MutableSharedFlow<ScrollJump>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val requests: SharedFlow<ScrollJump> = flow.asSharedFlow()

  fun request(jump: ScrollJump): Boolean = flow.tryEmit(jump)
}

fun Modifier.keepScrollJumps(): Modifier = onKeyEvent { event -> scrollJumpOf(event) != null }

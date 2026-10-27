package io.github.mataku.kdiffviewer.app.selection

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpOffset

data class ContextMenuItem(val label: String, val enabled: Boolean, val shortcut: Char? = null, val onClick: () -> Unit)

fun interface NativeContextMenu {
  fun show(positionInWindow: DpOffset, items: List<ContextMenuItem>)
}

val LocalNativeContextMenu = staticCompositionLocalOf<NativeContextMenu?> { null }

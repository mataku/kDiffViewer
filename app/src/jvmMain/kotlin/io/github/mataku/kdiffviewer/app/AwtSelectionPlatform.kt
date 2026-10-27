package io.github.mataku.kdiffviewer.app

import androidx.compose.ui.unit.DpOffset
import io.github.mataku.kdiffviewer.app.selection.ClipboardWriter
import io.github.mataku.kdiffviewer.app.selection.ContextMenuItem
import io.github.mataku.kdiffviewer.app.selection.NativeContextMenu
import java.awt.MenuItem
import java.awt.MenuShortcut
import java.awt.PopupMenu
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent

object AwtClipboardWriter : ClipboardWriter {
  override fun write(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
  }
}

class AwtNativeContextMenu(private val window: java.awt.Window) : NativeContextMenu {
  private var current: PopupMenu? = null

  override fun show(positionInWindow: DpOffset, items: List<ContextMenuItem>) {
    dismiss()
    val popup = PopupMenu()
    items.forEach { item ->
      val menuItem = MenuItem(item.label)
      menuItem.isEnabled = item.enabled
      item.shortcut?.let { menuItem.shortcut = MenuShortcut(KeyEvent.getExtendedKeyCodeForChar(it.code)) }
      menuItem.addActionListener {
        item.onClick()
        if (current === popup) dismiss()
      }
      popup.add(menuItem)
    }
    window.add(popup)
    current = popup
    val origin = (window as? javax.swing.RootPaneContainer)?.contentPane ?: window
    popup.show(origin, positionInWindow.x.value.toInt(), positionInWindow.y.value.toInt())
  }

  private fun dismiss() {
    current?.let { window.remove(it) }
    current = null
  }
}

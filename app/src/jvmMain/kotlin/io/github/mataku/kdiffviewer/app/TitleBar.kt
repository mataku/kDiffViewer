package io.github.mataku.kdiffviewer.app

import javax.swing.JRootPane

fun mergeTitleBarIntoContent(rootPane: JRootPane, osName: String = System.getProperty("os.name")): Boolean {
  if (!osName.startsWith("Mac")) return false
  rootPane.putClientProperty("apple.awt.fullWindowContent", true)
  rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
  rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
  return true
}

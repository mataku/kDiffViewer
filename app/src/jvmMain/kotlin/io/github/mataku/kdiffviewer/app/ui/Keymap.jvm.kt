package io.github.mataku.kdiffviewer.app.ui

actual val HostKeymap: Keymap = if (System.getProperty("os.name").startsWith("Mac")) Keymap.Mac else Keymap.Standard

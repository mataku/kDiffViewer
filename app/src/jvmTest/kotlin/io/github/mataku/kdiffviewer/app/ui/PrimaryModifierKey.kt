package io.github.mataku.kdiffviewer.app.ui

import androidx.compose.ui.input.key.Key

val PrimaryModifierKey: Key = if (HostKeymap == Keymap.Mac) Key.MetaLeft else Key.CtrlLeft

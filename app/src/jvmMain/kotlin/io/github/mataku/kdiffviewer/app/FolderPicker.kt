package io.github.mataku.kdiffviewer.app

import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JFileChooser

private const val DIRECTORY_DIALOG_PROPERTY = "apple.awt.fileDialogForDirectories"

fun pickFolder(): String? =
    if (System.getProperty("os.name").startsWith("Mac")) pickFolderOnMac() else pickFolderWithChooser()

private fun pickFolderOnMac(): String? {
    val previous = System.getProperty(DIRECTORY_DIALOG_PROPERTY)
    System.setProperty(DIRECTORY_DIALOG_PROPERTY, "true")
    try {
        val dialog = FileDialog(null as Frame?, "Open Repository", FileDialog.LOAD)
        dialog.isVisible = true
        val directory = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return java.io.File(directory, name).path
    } finally {
        if (previous == null) System.clearProperty(DIRECTORY_DIALOG_PROPERTY)
        else System.setProperty(DIRECTORY_DIALOG_PROPERTY, previous)
    }
}

private fun pickFolderWithChooser(): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Open Repository"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.path else null
}

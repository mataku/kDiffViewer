package io.github.mataku.kdiffviewer.core.git

import java.io.File

fun GitLocator.locateOnHost(): String? = locate(System.getenv("PATH")) { File(it).canExecute() }

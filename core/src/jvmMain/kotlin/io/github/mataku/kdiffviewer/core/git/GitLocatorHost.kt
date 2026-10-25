package io.github.mataku.kdiffviewer.core.git

import java.io.File

fun GitLocator.locateOnHost(pathEnv: String? = System.getenv("PATH")): String? =
    locate(pathEnv) { File(it).let { file -> file.isFile && file.canExecute() } }

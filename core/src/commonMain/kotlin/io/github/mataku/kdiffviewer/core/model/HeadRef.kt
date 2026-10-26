package io.github.mataku.kdiffviewer.core.model

sealed interface HeadRef {
  data class Branch(val name: String) : HeadRef

  data class Detached(val shortHash: String) : HeadRef
}

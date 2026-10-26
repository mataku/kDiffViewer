package io.github.mataku.kdiffviewer.core.model

sealed interface BaseSpec {
  data object Default : BaseSpec

  data class Ref(val ref: String) : BaseSpec
}

sealed interface ResolvedBase {
  data class Commit(val label: String, val id: String, val shortId: String) : ResolvedBase

  data object EmptyTree : ResolvedBase
}

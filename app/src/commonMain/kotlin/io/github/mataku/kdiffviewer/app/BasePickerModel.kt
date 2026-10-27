package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.BranchRef
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.ResolvedBase

internal data class BranchSections(val local: List<BranchRef>, val remote: List<BranchRef>)

internal fun sectionBranches(branches: List<BranchRef>, head: HeadRef?, query: String): BranchSections {
  val current = (head as? HeadRef.Branch)?.name
  val needle = query.trim()
  val (remote, local) = branches
    .filter { it.name.contains(needle, ignoreCase = true) }
    .partition { it.remote }
  return BranchSections(local.filter { it.name != current }, remote)
}

internal fun defaultEntryLabel(base: ResolvedBase.Commit, baseSpec: BaseSpec): String = if (baseSpec == BaseSpec.Default) "Default (${base.label})" else "Default"

internal sealed interface TypedRef {
  data class Valid(val spec: BaseSpec.Ref) : TypedRef

  data class Invalid(val message: String) : TypedRef

  data object Empty : TypedRef
}

internal fun parseTypedRef(text: String): TypedRef {
  val trimmed = text.trim()
  return when {
    trimmed.isEmpty() -> TypedRef.Empty
    trimmed.startsWith("-") -> TypedRef.Invalid("Invalid revision '$trimmed'")
    else -> TypedRef.Valid(BaseSpec.Ref(trimmed))
  }
}

internal fun pickerErrorText(error: ViewerError): String = when (error) {
  is ViewerError.UnknownBase -> "Unknown revision '${error.ref}'"
  is ViewerError.NoCommonAncestor -> "No common ancestor between '${error.ref}' and HEAD"
  is ViewerError.GitFailed -> error.stderr.trim().ifEmpty { "git failed" }
  else -> "Could not switch base"
}

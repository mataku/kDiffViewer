package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.BaseSpec
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.HeadRef
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.ResolvedBase
import io.github.mataku.kdiffviewer.core.parse.UnifiedDiffParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

interface DiffSource {
  suspend fun resolveBase(repo: RepoPath, spec: BaseSpec): ResolvedBase

  fun load(repo: RepoPath, base: ResolvedBase): Flow<FileDiff>

  suspend fun head(repo: RepoPath): HeadRef?

  suspend fun root(repo: RepoPath): RepoPath? = null
}

sealed class DiffLoadException(message: String) : Exception(message) {
  class NotARepository(val path: String) : DiffLoadException("Not a git repository: $path")

  class UnknownBase(val ref: String) : DiffLoadException("Unknown base revision '$ref'")

  class NoCommonAncestor(val ref: String) : DiffLoadException("No common ancestor between '$ref' and HEAD")

  class GitFailed(
    val args: List<String>,
    val exitCode: Int,
    val stderr: String,
  ) : DiffLoadException("git ${args.joinToString(" ")} failed with exit code $exitCode: $stderr")
}

private const val EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"

class GitDiffSource(
  private val runner: GitCommandRunner,
  private val reader: WorkingTreeReader,
) : DiffSource {
  override fun load(repo: RepoPath, base: ResolvedBase): Flow<FileDiff> = flow {
    val rootResult = runner.run(repo.value, listOf("rev-parse", "--show-toplevel"))
    val root = rootResult.stdout.trimEnd('\n')
    if (rootResult.exitCode != 0 || root.isEmpty()) throw DiffLoadException.NotARepository(repo.value)

    val baseId = when (base) {
      is ResolvedBase.Commit -> base.id
      ResolvedBase.EmptyTree -> EMPTY_TREE
    }
    val diffArgs = listOf(
      "-c", "core.quotePath=false", "diff", baseId,
      "--no-color", "--no-ext-diff", "--no-textconv", "--src-prefix=a/", "--dst-prefix=b/", "-M", "--submodule=short",
    )
    emitAll(
      UnifiedDiffParser.parse(runner.streamLines(root, diffArgs)).catch { e ->
        if (e is GitCommandFailedException) {
          throw DiffLoadException.GitFailed(e.args, e.exitCode, e.stderr)
        }
        throw e
      },
    )

    val lsArgs = listOf("ls-files", "--others", "--exclude-standard", "-z")
    val ls = runner.run(root, lsArgs)
    if (ls.exitCode != 0) throw DiffLoadException.GitFailed(lsArgs, ls.exitCode, ls.stderr)
    for (path in ls.stdout.split('\u0000')) {
      if (path.isEmpty() || path.endsWith("/")) continue
      emit(synthesizeUntracked(path, reader.read(root, path, UNTRACKED_SIZE_LIMIT)))
    }
  }

  override suspend fun resolveBase(repo: RepoPath, spec: BaseSpec): ResolvedBase {
    val dir = repo.value
    val toplevel = runner.run(dir, listOf("rev-parse", "--show-toplevel"))
    if (toplevel.exitCode != 0 || toplevel.stdout.trimEnd('\n').isEmpty()) throw DiffLoadException.NotARepository(dir)
    val head = runner.run(dir, listOf("rev-parse", "--verify", "HEAD"))
    val headId = head.stdout.trim().takeIf { head.exitCode == 0 && it.isNotEmpty() }
    return when (spec) {
      BaseSpec.Default -> {
        if (headId == null) return ResolvedBase.EmptyTree
        val originHead = runner.run(dir, listOf("symbolic-ref", "--quiet", "refs/remotes/origin/HEAD"))
        val target = originHead.stdout.trim().takeIf { originHead.exitCode == 0 && it.isNotEmpty() }
        val mergeBase = target?.let { runner.run(dir, listOf("merge-base", "--end-of-options", it, headId)) }
        val baseId = mergeBase?.stdout?.trim()?.takeIf { mergeBase.exitCode == 0 && it.isNotEmpty() }
        if (target == null || baseId == null) {
          commit(dir, "HEAD", headId)
        } else {
          commit(dir, target.removePrefix("refs/remotes/"), baseId)
        }
      }

      is BaseSpec.Ref -> {
        val verify = runner.run(dir, listOf("rev-parse", "--verify", "--quiet", "--end-of-options", "${spec.ref}^{commit}"))
        val refId = verify.stdout.trim().takeIf { verify.exitCode == 0 && it.isNotEmpty() }
          ?: throw DiffLoadException.UnknownBase(spec.ref)
        if (headId == null) throw DiffLoadException.NoCommonAncestor(spec.ref)
        val mergeBaseArgs = listOf("merge-base", "--end-of-options", refId, headId)
        val mergeBase = runner.run(dir, mergeBaseArgs)
        when (mergeBase.exitCode) {
          0 -> commit(dir, spec.ref, mergeBase.stdout.trim())
          1 -> throw DiffLoadException.NoCommonAncestor(spec.ref)
          else -> throw DiffLoadException.GitFailed(mergeBaseArgs, mergeBase.exitCode, mergeBase.stderr)
        }
      }
    }
  }

  private suspend fun commit(dir: String, label: String, id: String): ResolvedBase.Commit {
    val short = runner.run(dir, listOf("rev-parse", "--short", id))
    val shortId = short.stdout.trim().takeIf { short.exitCode == 0 && it.isNotEmpty() } ?: id.take(7)
    return ResolvedBase.Commit(label, id, shortId)
  }

  override suspend fun root(repo: RepoPath): RepoPath? {
    val result = runner.run(repo.value, listOf("rev-parse", "--show-toplevel"))
    val root = result.stdout.trimEnd('\n')
    return if (result.exitCode == 0 && root.isNotEmpty()) RepoPath(root) else null
  }

  override suspend fun head(repo: RepoPath): HeadRef? {
    val branch = runner.run(repo.value, listOf("symbolic-ref", "--quiet", "--short", "HEAD"))
    if (branch.exitCode == 0) return HeadRef.Branch(branch.stdout.trimEnd('\n'))
    val hash = runner.run(repo.value, listOf("rev-parse", "--short", "HEAD"))
    return if (hash.exitCode == 0) HeadRef.Detached(hash.stdout.trimEnd('\n')) else null
  }
}

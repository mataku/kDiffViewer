package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.parse.UnifiedDiffParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

interface DiffSource {
    fun load(repo: RepoPath): Flow<FileDiff>
}

sealed class DiffLoadException(message: String) : Exception(message) {
    class NotARepository(val path: String) : DiffLoadException("Not a git repository: $path")

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
    override fun load(repo: RepoPath): Flow<FileDiff> = flow {
        val rootResult = runner.run(repo.value, listOf("rev-parse", "--show-toplevel"))
        val root = rootResult.stdout.trimEnd('\n')
        if (rootResult.exitCode != 0 || root.isEmpty()) throw DiffLoadException.NotARepository(repo.value)

        val head = runner.run(root, listOf("rev-parse", "--verify", "HEAD"))
        val base = if (head.exitCode == 0) "HEAD" else EMPTY_TREE

        val diffArgs = listOf(
            "-c", "core.quotePath=false", "diff", base,
            "--no-color", "--no-ext-diff", "--src-prefix=a/", "--dst-prefix=b/", "-M", "--submodule=short",
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
}

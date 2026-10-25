package io.github.mataku.kdiffviewer.core.git

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.FileDiff
import io.github.mataku.kdiffviewer.core.model.RepoPath
import io.github.mataku.kdiffviewer.core.model.displayPath
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitDiffSourceIntegrationTest {
    private val repo = newTempDir()
    private val source = GitDiffSource(ProcessGitCommandRunner(requireGit()), FileSystemWorkingTreeReader())

    private fun initRepo() {
        runGit(repo, "init", "-b", "main")
    }

    private fun write(path: String, text: String) {
        val file = File(repo, path)
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    private fun commitAll(message: String = "c") {
        runGit(repo, "add", "-A")
        runGit(repo, "commit", "-m", message)
    }

    private fun load(path: String = repo.path): List<FileDiff> =
        runBlocking { source.load(RepoPath(path)).toList() }

    private fun List<FileDiff>.byPath(path: String) = single { it.displayPath == path }

    @Test
    fun stagedUnstagedAndUntracked() {
        initRepo()
        write("a.txt", "1\n2\n")
        write("b.txt", "x\ny\n")
        commitAll()
        write("a.txt", "1\n2\n3\n")
        runGit(repo, "add", "a.txt")
        write("b.txt", "x\nz\n")
        write("c.txt", "new\n")

        val files = load()

        assertEquals(listOf("a.txt", "b.txt", "c.txt"), files.map { it.displayPath })
        assertEquals(ChangeType.Modified, files.byPath("a.txt").change)
        assertEquals(ChangeType.Modified, files.byPath("b.txt").change)
        assertEquals(ChangeType.Added, files.byPath("c.txt").change)
        assertTrue(files.byPath("c.txt").isUntracked)
        assertFalse(files.byPath("a.txt").isUntracked)
    }

    @Test
    fun repositoryWithoutCommits() {
        initRepo()
        write("staged.txt", "s\n")
        runGit(repo, "add", "staged.txt")
        write("loose.txt", "l\n")

        val files = load()

        assertEquals(ChangeType.Added, files.byPath("staged.txt").change)
        assertFalse(files.byPath("staged.txt").isUntracked)
        assertEquals(ChangeType.Added, files.byPath("loose.txt").change)
        assertTrue(files.byPath("loose.txt").isUntracked)
    }

    @Test
    fun noprefixConfigIsIgnored() {
        initRepo()
        write("a.txt", "1\n")
        commitAll()
        runGit(repo, "config", "diff.noprefix", "true")
        write("a.txt", "2\n")

        assertEquals("a.txt", load().single().displayPath)
    }

    @Test
    fun renameIsDetected() {
        initRepo()
        write("a.txt", "one\ntwo\nthree\nfour\nfive\n")
        commitAll()
        runGit(repo, "mv", "a.txt", "b.txt")

        val file = load().single()

        assertEquals(ChangeType.Renamed, file.change)
        assertEquals("a.txt", file.oldPath)
        assertEquals("b.txt", file.newPath)
    }

    @Test
    fun subdirectoryOpensWholeRepository() {
        initRepo()
        write("sub/in.txt", "1\n")
        write("out.txt", "1\n")
        commitAll()
        write("out.txt", "2\n")

        val files = load(File(repo, "sub").path)

        assertNotNull(files.firstOrNull { it.displayPath == "out.txt" })
    }

    @Test
    fun notARepository() {
        val e = assertFailsWith<DiffLoadException.NotARepository> { load() }
        assertEquals(repo.path, e.path)
    }

    @Test
    fun nonexistentPath() {
        val missing = File(repo, "missing").path
        val e = assertFailsWith<DiffLoadException.NotARepository> { load(missing) }
        assertEquals(missing, e.path)
    }

    @Test
    fun sameFileDeletedAndUntracked() {
        initRepo()
        write("a.txt", "1\n")
        commitAll()
        runGit(repo, "rm", "--cached", "a.txt")

        val files = load()

        assertEquals(2, files.size)
        assertTrue(files.all { it.displayPath == "a.txt" })
        val deleted = files.single { it.change == ChangeType.Deleted }
        val added = files.single { it.change == ChangeType.Added }
        assertFalse(deleted.isUntracked)
        assertTrue(added.isUntracked)
    }
}

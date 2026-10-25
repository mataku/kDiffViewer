package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaunchArgsTest {
    @Test
    fun noArgsMeansNoRepo() {
        assertNull(parseLaunchArgs(emptyArray(), "/x"))
    }

    @Test
    fun relativeArgIsResolved() {
        assertEquals(RepoPath("/a/r"), parseLaunchArgs(arrayOf("../r"), "/a/b"))
    }

    @Test
    fun absoluteArgIsKept() {
        assertEquals(RepoPath("/r/s"), parseLaunchArgs(arrayOf("/r/./s"), "/a"))
    }
}

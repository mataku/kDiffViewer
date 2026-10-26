package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.DiffContent
import io.github.mataku.kdiffviewer.core.model.FileDiff
import kotlin.test.Test
import kotlin.test.assertEquals

class GitFileStatusTest {
  private fun file(change: ChangeType, untracked: Boolean = false) = FileDiff("a", "a", change, untracked, DiffContent.Text(emptyList()))

  @Test
  fun untrackedWinsOverAdded() {
    assertEquals(GitFileStatus.Untracked, file(ChangeType.Added, untracked = true).gitStatus())
  }

  @Test
  fun addedMapsToAdded() {
    assertEquals(GitFileStatus.Added, file(ChangeType.Added).gitStatus())
  }

  @Test
  fun modifiedMapsToModified() {
    assertEquals(GitFileStatus.Modified, file(ChangeType.Modified).gitStatus())
  }

  @Test
  fun deletedMapsToDeleted() {
    assertEquals(GitFileStatus.Deleted, file(ChangeType.Deleted).gitStatus())
  }

  @Test
  fun renamedMapsToRenamed() {
    assertEquals(GitFileStatus.Renamed, file(ChangeType.Renamed).gitStatus())
  }
}

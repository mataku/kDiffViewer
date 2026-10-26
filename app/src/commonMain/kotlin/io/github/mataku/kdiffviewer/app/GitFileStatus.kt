package io.github.mataku.kdiffviewer.app

import io.github.mataku.kdiffviewer.core.model.ChangeType
import io.github.mataku.kdiffviewer.core.model.FileDiff

enum class GitFileStatus { Untracked, Added, Modified, Deleted, Renamed }

fun FileDiff.gitStatus(): GitFileStatus = when {
  isUntracked -> GitFileStatus.Untracked

  else -> when (change) {
    ChangeType.Added -> GitFileStatus.Added
    ChangeType.Modified -> GitFileStatus.Modified
    ChangeType.Deleted -> GitFileStatus.Deleted
    ChangeType.Renamed -> GitFileStatus.Renamed
  }
}

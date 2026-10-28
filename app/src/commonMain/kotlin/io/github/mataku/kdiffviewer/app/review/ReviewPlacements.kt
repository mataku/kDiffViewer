package io.github.mataku.kdiffviewer.app.review

import androidx.compose.runtime.Immutable
import io.github.mataku.kdiffviewer.core.review.Anchor
import io.github.mataku.kdiffviewer.core.review.Placement
import io.github.mataku.kdiffviewer.core.review.ReviewThread

fun linePosition(fileIndex: Int, hunkIndex: Int, lineIndex: Int): Long = (fileIndex.toLong() shl 40) or (hunkIndex.toLong() shl 20) or lineIndex.toLong()

@Immutable
class ReviewPlacements(
  val loadId: Long,
  val byLine: Map<Long, List<ReviewThread>>,
  val byFile: Map<Int, List<ReviewThread>>,
  val orphans: List<ReviewThread>,
) {
  companion object {
    val Empty = ReviewPlacements(0L, emptyMap(), emptyMap(), emptyList())
  }
}

sealed interface DraftTarget {
  data class NewThread(val anchor: Anchor, val placement: Placement) : DraftTarget

  data class Reply(val threadId: String) : DraftTarget

  data class Edit(val commentId: String) : DraftTarget
}

data class Draft(val target: DraftTarget, val text: String)

fun DraftTarget?.isOrphanThread(): Boolean = this is DraftTarget.NewThread && placement == Placement.Orphan

fun ReviewPlacements.notesFiles(target: DraftTarget?): Set<Int> {
  val draftFile = if (target is DraftTarget.NewThread) (target.placement as? Placement.OnFile)?.fileIndex else null
  if (draftFile == null) return byFile.keys
  return byFile.keys + draftFile
}

fun ReviewPlacements.threadFiles(): Set<Int> {
  val files = HashSet<Int>(byFile.keys)
  for (position in byLine.keys) files += (position shr 40).toInt()
  return files
}

internal fun buildPlacements(loadId: Long, threads: List<ReviewThread>, placementById: Map<String, Placement>): ReviewPlacements {
  val byLine = LinkedHashMap<Long, MutableList<ReviewThread>>()
  val byFile = LinkedHashMap<Int, MutableList<ReviewThread>>()
  val orphans = ArrayList<ReviewThread>()
  for (thread in threads) {
    when (val placement = placementById[thread.id]) {
      is Placement.OnLine -> {
        val location = placement.location
        byLine.getOrPut(linePosition(location.fileIndex, location.hunkIndex, location.lineIndex)) { ArrayList() }.add(thread)
      }

      is Placement.OnFile -> byFile.getOrPut(placement.fileIndex) { ArrayList() }.add(thread)

      Placement.Orphan, null -> orphans.add(thread)
    }
  }
  return ReviewPlacements(loadId, byLine, byFile, orphans)
}

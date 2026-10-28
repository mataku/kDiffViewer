package io.github.mataku.kdiffviewer.app.review

import io.github.mataku.kdiffviewer.core.model.Side
import io.github.mataku.kdiffviewer.core.review.Side as ReviewSide

fun Side.toReviewSide(): ReviewSide = when (this) {
  Side.Old -> ReviewSide.Old
  Side.New -> ReviewSide.New
}

fun ReviewSide.toSide(): Side = when (this) {
  ReviewSide.Old -> Side.Old
  ReviewSide.New -> Side.New
}

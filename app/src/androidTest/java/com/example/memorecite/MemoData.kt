package com.example.memorecite

import java.util.UUID

data class MemoCard(
    val id: String = UUID.randomUUID().toString(),
    val front: String = "",
    val back: String = "",
    var reviewLevel: Int = 0,
    var easeFactor: Double = 2.5,
    var lastReviewTime: Long = 0L,
    var nextReviewTime: Long = 0L
) {
    val isNew: Boolean get() = lastReviewTime == 0L
    fun isDue(now: Long = System.currentTimeMillis()): Boolean =
        !isNew && nextReviewTime <= now
}

data class MemoGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val cards: MutableList<MemoCard> = mutableListOf()
)
package com.example.memorecite

object EbbinghausScheduler {
    // 艾宾浩斯标准复习间隔（分钟）：5分 → 30分 → 12时 → 1天 → 2天 → 4天 → 7天 → 15天 → 30天 → 60天
    private val INTERVALS = longArrayOf(
        5L,
        30L,
        12 * 60L,
        24 * 60L,
        2 * 24 * 60L,
        4 * 24 * 60L,
        7 * 24 * 60L,
        15 * 24 * 60L,
        30 * 24 * 60L,
        60 * 24 * 60L
    )

    const val QUALITY_FORGOT = 0   // 忘了
    const val QUALITY_VAGUE = 1    // 模糊
    const val QUALITY_GOOD = 2     // 记住了

    fun initNewCard(card: MemoCard, now: Long = System.currentTimeMillis()) {
        card.reviewLevel = 0
        card.easeFactor = 2.5
        card.lastReviewTime = 0L
        card.nextReviewTime = now + 60 * 1000L // 新卡片 1 分钟后首次出现
    }

    fun schedule(card: MemoCard, quality: Int, now: Long = System.currentTimeMillis()) {
        when (quality) {
            QUALITY_FORGOT -> {
                card.reviewLevel = 0
                card.easeFactor = maxOf(1.3, card.easeFactor - 0.2)
            }
            QUALITY_VAGUE -> {
                card.easeFactor = maxOf(1.3, card.easeFactor - 0.15)
            }
            QUALITY_GOOD -> {
                card.reviewLevel = (card.reviewLevel + 1).coerceAtMost(INTERVALS.size - 1)
                card.easeFactor = minOf(2.5, card.easeFactor + 0.1)
            }
        }

        val base = INTERVALS[card.reviewLevel].toDouble()
        val factor = card.easeFactor / 2.5
        val minutes = if (quality == QUALITY_VAGUE) {
            (base * 0.5).toLong().coerceAtLeast(5L)
        } else {
            (base * factor).toLong().coerceAtLeast(5L)
        }

        card.lastReviewTime = now
        card.nextReviewTime = now + minutes * 60 * 1000L
    }

    fun describeNext(card: MemoCard): String {
        if (card.isNew) return "新卡片"
        val diff = card.nextReviewTime - System.currentTimeMillis()
        if (diff <= 0) return "待复习"
        val minutes = diff / (60 * 1000)
        return when {
            minutes < 60 -> "${minutes} 分钟后"
            minutes < 24 * 60 -> "${minutes / 60} 小时后"
            else -> "${minutes / (24 * 60)} 天后"
        }
    }
}
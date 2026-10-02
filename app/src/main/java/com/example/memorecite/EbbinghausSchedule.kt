package com.example.memorecite

import android.content.Context

object EbbinghausScheduler {
    const val QUALITY_FORGOT = 0
    const val QUALITY_VAGUE = 1
    const val QUALITY_GOOD = 2

    fun initNewCard(card: MemoCard, firstDelayMinutes: Long, now: Long = System.currentTimeMillis()) {
        card.reviewLevel = 0
        card.easeFactor = 2.5
        card.lastReviewTime = 0L
        card.nextReviewTime = now + firstDelayMinutes * 60_000L
    }

    fun schedule(card: MemoCard, quality: Int, intervals: List<Long>, now: Long = System.currentTimeMillis()) {
        when (quality) {
            QUALITY_FORGOT -> {
                card.reviewLevel = 0
                card.easeFactor = maxOf(1.3, card.easeFactor - 0.2)
            }
            QUALITY_VAGUE -> {
                card.easeFactor = maxOf(1.3, card.easeFactor - 0.15)
            }
            QUALITY_GOOD -> {
                card.reviewLevel = (card.reviewLevel + 1).coerceAtMost(intervals.size - 1)
                card.easeFactor = minOf(2.5, card.easeFactor + 0.1)
            }
        }

        val base = intervals.getOrElse(card.reviewLevel) { intervals.lastOrNull() ?: 60L }.toDouble()
        val factor = card.easeFactor / 2.5
        val minutes = if (quality == QUALITY_VAGUE) {
            (base * 0.5).toLong().coerceAtLeast(1L)
        } else {
            (base * factor).toLong().coerceAtLeast(1L)
        }

        card.lastReviewTime = now
        card.nextReviewTime = now + minutes * 60_000L
    }

    /** 卡片状态描述（需要 Context 来取本地化字符串） */
    fun describeNext(ctx: Context, card: MemoCard): String {
        if (card.nextReviewTime == 0L) return ""

        val diff = card.nextReviewTime - System.currentTimeMillis()

        return if (diff <= 0) {
            val overdue = -diff
            ctx.getString(R.string.md_due_overdue, formatElapsed(ctx, overdue))
        } else {
            if (card.isNew) {
                ctx.getString(R.string.md_countdown_new, formatElapsed(ctx, diff))
            } else {
                ctx.getString(R.string.md_countdown, formatElapsed(ctx, diff))
            }
        }
    }

    /** 纯倒计时格式（不带前缀） */
    fun formatCountdown(ctx: Context, nextTime: Long): String {
        val diff = nextTime - System.currentTimeMillis()
        if (diff <= 0) return ctx.getString(R.string.md_due)
        return formatElapsed(ctx, diff)
    }

    private fun formatElapsed(ctx: Context, millis: Long): String {
        val sec = millis / 1000
        val min = sec / 60
        val hour = min / 60
        val day = hour / 24
        return when {
            sec < 60 -> ctx.getString(R.string.md_seconds, sec)
            min < 60 -> ctx.getString(R.string.md_minutes, min)
            hour < 24 -> ctx.getString(R.string.md_hours, hour)
            else -> ctx.getString(R.string.md_days, day)
        }
    }

    val PRESET_CURVES = linkedMapOf(
        "curve_preset_default" to listOf(5L, 30L, 720L, 1440L, 2880L, 5760L, 10080L, 21600L, 43200L, 86400L),
        "curve_preset_dense" to listOf(1L, 5L, 15L, 30L, 60L, 120L, 360L, 720L, 1440L, 4320L),
        "curve_preset_loose" to listOf(30L, 360L, 1440L, 4320L, 10080L, 21600L, 43200L, 86400L, 172800L),
        "curve_preset_easy" to listOf(1440L, 4320L, 10080L, 43200L, 86400L, 259200L),
        "curve_preset_stubborn" to listOf(5L, 10L, 20L, 40L, 80L, 160L, 320L, 640L, 1280L, 2560L)
    )
}
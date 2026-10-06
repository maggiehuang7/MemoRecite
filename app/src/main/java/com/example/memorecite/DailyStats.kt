package com.example.memorecite

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DailyStats(
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date()),
    var newCardsTarget: Int = 80,
    var newCardsDone: Int = 0,
    var reviewsTarget: Int = 0,
    var reviewsDone: Int = 0,
    var totalInteractions: Int = 0,
    var correctCount: Int = 0,
    var wrongCount: Int = 0,
    var completedAt: Long = 0L,
    var streakDay: Int = 0
) {
    val totalAnswers: Int get() = correctCount + wrongCount

    val correctRate: Double
        get() = if (totalAnswers == 0) 0.0 else (correctCount.toDouble() / totalAnswers * 100.0)

    val isCompleted: Boolean
        get() {
            val reqNew = newCardsDone >= newCardsTarget
            val reqReview = reviewsDone >= reviewsTarget
            val reqRate = correctRate >= 70.0
            val reqInteractions = totalInteractions >= (11 * newCardsTarget)
            return reqNew && reqReview && reqRate && reqInteractions
        }
}

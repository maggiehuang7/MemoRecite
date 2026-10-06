package com.example.memorecite

import android.content.Context
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DailyStatsStore {
    private const val PREFS_NAME = "daily_stats_prefs"
    private const val KEY_STREAK = "streak_days"
    private val gson = Gson()

    fun todayStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    }

    fun dateMinusDays(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.time)
    }

    fun load(context: Context, date: String = todayStr()): DailyStats {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("stats_$date", null)
        val target = Prefs.getDailyNewTarget(context)

        val stats = if (json != null) {
            try {
                gson.fromJson(json, DailyStats::class.java) ?: DailyStats(date = date, newCardsTarget = target)
            } catch (_: Exception) {
                DailyStats(date = date, newCardsTarget = target)
            }
        } else {
            DailyStats(date = date, newCardsTarget = target)
        }

        // 动态校准当日目标新卡数和复习目标卡片数
        if (date == todayStr()) {
            stats.newCardsTarget = target
            val groups = MemoStore.load(context)
            val now = System.currentTimeMillis()
            val dueCount = groups.sumOf { g ->
                if (g.isStarted(groups, now)) {
                    g.allCardsRecursive(groups).count { it.isDue(now) }
                } else 0
            }
            stats.reviewsTarget = (stats.reviewsDone + dueCount).coerceAtLeast(stats.reviewsTarget)
        }
        return stats
    }

    fun save(context: Context, stats: DailyStats) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(stats)
        sp.edit().putString("stats_${stats.date}", json).apply()
    }

    fun getStreak(context: Context): Int {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getInt(KEY_STREAK, 0)
    }

    fun updateStreak(context: Context): Int {
        var streak = 0
        var i = 0
        while (i < 365) {
            val d = dateMinusDays(i)
            val stats = load(context, d)
            if (i == 0 && !stats.isCompleted) {
                i++
                continue
            }
            if (stats.isCompleted) {
                streak++
            } else {
                break
            }
            i++
        }
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putInt(KEY_STREAK, streak).apply()
        return streak
    }

    fun checkArrears(context: Context): Boolean {
        val yesterday = load(context, dateMinusDays(1))
        val dayBeforeYesterday = load(context, dateMinusDays(2))
        return !yesterday.isCompleted && !dayBeforeYesterday.isCompleted
    }

    fun loadRecentDays(context: Context, days: Int = 7): List<DailyStats> {
        val list = mutableListOf<DailyStats>()
        for (i in 0 until days) {
            val d = dateMinusDays(i)
            list.add(load(context, d))
        }
        return list
    }
}

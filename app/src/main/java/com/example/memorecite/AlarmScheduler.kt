package com.example.memorecite

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    private const val REQUEST_CODE = 1001

    fun scheduleNext(context: Context) {
        val now = System.currentTimeMillis()
        val groups = MemoStore.load(context)

        // 🟢 只统计"已开始"的组（用 effectiveStartAt 判断继承）
        val allCards = groups
            .filter { it.isStarted(groups, now) }
            .flatMap { it.cards }

        if (allCards.isEmpty()) {
            cancel(context)
            return
        }

        val hasReady = allCards.any { it.isDue(now) || it.isNew }
        val cooldown = ScreenState.cooldownRemaining(context)
        val quietRemaining = Prefs.quietHoursRemaining(context)

        val triggerAt = when {
            // 免打扰时段 → 延后到免打扰结束
            quietRemaining > 0 -> now + quietRemaining + 5_000L

            // 有可弹卡片 + 冷却中 → 延后到冷却结束
            hasReady && cooldown > 0 -> now + cooldown + 1000L

            // 有可弹卡片 + 冷却已过 → 60 秒后触发
            hasReady -> now + 60_000L

            // 没有可弹卡片 → 找未来最近的
            else -> {
                val nextFuture = allCards
                    .filter { !it.isNew }
                    .map { it.nextReviewTime }
                    .filter { it > now }
                    .minOrNull()

                nextFuture ?: run {
                    cancel(context)
                    return
                }
            }
        }

        setAlarm(context, triggerAt)
    }

    private fun setAlarm(context: Context, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, MemoAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val info = AlarmManager.AlarmClockInfo(triggerAt, pi)
            am.setAlarmClock(info, pi)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, MemoAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
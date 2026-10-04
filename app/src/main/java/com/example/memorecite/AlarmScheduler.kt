package com.example.memorecite

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    private const val REQUEST_CODE = 1001

    @Volatile
    private var lastScheduledTriggerAt: Long = 0L

    fun scheduleNext(context: Context) {
        // 如果卡片界面正在显示，不需要重复设置即时闹钟
        if (MemoDisplayActivity.isShowing) return

        val now = System.currentTimeMillis()
        val groups = MemoStore.load(context)

        // 🟢 统计所有已开始组及其子组的所有卡片
        val allCards = groups
            .filter { it.isStarted(groups, now) }
            .flatMap { it.allCardsRecursive(groups) }

        val hasReady = allCards.any { it.isDue(now) || it.isNew }
        val quietRemaining = Prefs.quietHoursRemaining(context)

        val triggerAt = when {
            // 免打扰时段 → 延后到免打扰结束
            quietRemaining > 0 -> now + quietRemaining + 5_000L

            // 有可弹卡片 → 1 秒后触发（即时弹卡提醒）
            hasReady -> now + 1_000L

            // 没有可弹卡片 → 找未来最近的复习时间；若没有未来卡片，则设置 3 分钟后兜底轮询（绝不彻底取消闹钟）
            else -> {
                val nextFuture = allCards
                    .filter { !it.isNew }
                    .map { it.nextReviewTime }
                    .filter { it > now }
                    .minOrNull()

                nextFuture ?: (now + 3 * 60 * 1000L)
            }
        }

        // 防抖：如果设置的目标触发时间跟上一次相比相差不到 5 秒，且上一次设置的时间还没过期，就不重复重置 AlarmManager
        if (lastScheduledTriggerAt > now && Math.abs(triggerAt - lastScheduledTriggerAt) < 5_000L) {
            return
        }

        lastScheduledTriggerAt = triggerAt
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
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (_: Exception) {}
        }
    }

    fun cancel(context: Context) {
        lastScheduledTriggerAt = 0L
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, MemoAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
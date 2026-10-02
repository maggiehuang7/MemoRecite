package com.example.memorecite

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

class MemoAlarmReceiver : BroadcastReceiver() {

    companion object {
        @Volatile
        private var lastVibrateTime: Long = 0L
        private const val VIBRATE_COOLDOWN_MS = 5_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 🟢 检测是否是"起床闹钟"
        val isWakeup = intent.getBooleanExtra("wakeup", false)

        if (isWakeup) {
            handleWakeup(context)
            return
        }

        // 普通闹钟逻辑
        handleNormal(context)
    }

    /**
     * 🟢 起床闹钟：免打扰结束 → 立即弹卡
     */
    private fun handleWakeup(context: Context) {
        // 万一时间不准，还在免打扰中 → 重新设置
        if (Prefs.isInQuietHours(context)) {
            val quietEnd = Prefs.getQuietEndTime(context)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, 1002,
                Intent(context, MemoAlarmReceiver::class.java).apply {
                    putExtra("wakeup", true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                val info = AlarmManager.AlarmClockInfo(quietEnd + 5_000L, pi)
                am.setAlarmClock(info, pi)
            } catch (_: Exception) {}
            return
        }

        // 免打扰结束 → 立即弹卡
        showCard(context)
        AlarmScheduler.scheduleNext(context)
    }

    /**
     * 普通闹钟逻辑
     */
    private fun handleNormal(context: Context) {
        // 1. 免打扰时段 → 转交给起床闹钟
        if (Prefs.isInQuietHours(context)) {
            val quietEnd = Prefs.getQuietEndTime(context)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, 1002,
                Intent(context, MemoAlarmReceiver::class.java).apply {
                    putExtra("wakeup", true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                val info = AlarmManager.AlarmClockInfo(quietEnd + 5_000L, pi)
                am.setAlarmClock(info, pi)
            } catch (_: Exception) {}
            return
        }

        // 2. 未锁屏 + App 前台 → 延后
        val isLocked = ScreenState.isLocked(context)
        if (App.isForeground && !isLocked) {
            postphone(context, 2 * 60 * 1000L)
            return
        }

        // 3. 暂停窗口 → 延后
        if (Prefs.isPaused(context)) {
            postphone(context, 60 * 1000L)
            return
        }

        // 4. 未锁屏 → 延后
        if (!isLocked) {
            postphone(context, 60 * 1000L)
            return
        }

        // 5. 冷却期 → 延后
        if (!ScreenState.canAutoShow(context)) {
            val cooldown = ScreenState.cooldownRemaining(context)
            if (cooldown > 0) {
                postphone(context, cooldown + 1000L)
            } else {
                postphone(context, 60 * 1000L)
            }
            return
        }

        // 通过所有检查 → 弹卡 + 震动
        showCard(context)
        AlarmScheduler.scheduleNext(context)
    }

    private fun showCard(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MemoRecite::WakeLock"
        )
        wakeLock.acquire(10_000L)

        vibrateOnce(context)

        val displayIntent = Intent(context, MemoDisplayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            context.startActivity(displayIntent)
        } catch (_: Exception) {}

        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun postphone(context: Context, delayMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, 1001,
            Intent(context, MemoAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delayMillis

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
        } catch (_: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun vibrateOnce(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastVibrateTime < VIBRATE_COOLDOWN_MS) return
        lastVibrateTime = now

        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (!v.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(400L)
            }
        } catch (_: Exception) {}
    }
}
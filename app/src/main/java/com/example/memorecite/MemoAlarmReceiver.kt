package com.example.memorecite

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MemoAlarmReceiver : BroadcastReceiver() {

    companion object {
        @Volatile
        private var lastVibrateTime: Long = 0L
        private const val VIBRATE_COOLDOWN_MS = 60_000L
        private const val CHANNEL_ID = "memo_alarm_channel_v2"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val isFromUserWake = intent.getBooleanExtra("from_user_wake", false)

        // 免打扰 / 前台 / 暂停 / 正在弹卡中 → 跳过并重新调度
        if (Prefs.isInQuietHours(context))   { AlarmScheduler.scheduleNext(context); return }
        if (App.isForeground)               { AlarmScheduler.scheduleNext(context); return }
        if (Prefs.isPaused(context))        { AlarmScheduler.scheduleNext(context); return }
        if (MemoDisplayActivity.isShowing)  { AlarmScheduler.scheduleNext(context); return }

        if (!isFromUserWake && !ScreenState.canAutoShow(context)) {
            AlarmScheduler.scheduleNext(context)
            return
        }

        // 检查是否有待复习或新卡片（包含子分组中的卡片）
        val groups = MemoStore.load(context)
        val now = System.currentTimeMillis()
        val hasCards = groups.any { g ->
            !Prefs.isGroupSkipped(context, g.id) &&
                    g.isStarted(groups, now) &&
                    g.allCardsRecursive(groups).any { it.isDue(now) || it.isNew }
        }
        if (!hasCards) {
            AlarmScheduler.scheduleNext(context)
            return
        }

        // 1) 唤醒屏幕
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MemoRecite::WakeLock"
        )
        wakeLock.acquire(10_000L)

        // 2) 震动
        vibrateOnce(context)

        // 3) 全屏通知（Android 10+ 后台启动 Activity 被拦，靠它兜底）
        showFullScreenNotification(context)

        // 4) 同时尝试直接 startActivity
        try {
            context.startActivity(Intent(context, MemoDisplayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        } catch (_: Exception) {}

        AlarmScheduler.scheduleNext(context)
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun showFullScreenNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "背诵提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(ch)
        }

        val pi = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MemoDisplayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("背诵提醒")
            .setContentText("锁屏卡片已就绪")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setAutoCancel(true)
            .build()

        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun vibrateOnce(context: Context) {
        // 用户正在使用 App 或正在看卡片界面时，不需要震动提醒
        if (App.isForeground || MemoDisplayActivity.isShowing) return

        val now = System.currentTimeMillis()
        if (now - lastVibrateTime < VIBRATE_COOLDOWN_MS) return
        lastVibrateTime = now

        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (!v.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(200L)
            }
        } catch (_: Exception) {}
    }
}
package com.example.memorecite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

class ScreenStateReceiver : BroadcastReceiver() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> handleScreenOff(context)
            Intent.ACTION_SCREEN_ON -> handleScreenOn(context)
        }
    }

    private fun handleScreenOff(context: Context) {
        Prefs.clearPause(context)
        AlarmScheduler.scheduleNext(context)
    }

    private fun handleScreenOn(context: Context) {
        Prefs.clearPause(context)

        // 延迟 200ms 检查亮屏弹卡（让系统界面先亮起）
        handler.postDelayed({
            checkAndShow(context)
        }, 200L)

        // 1000ms 兜底检查一次，确保弹屏被成功调起
        handler.postDelayed({
            if (!MemoDisplayActivity.isShowing) {
                checkAndShow(context)
            }
        }, 1000L)
    }

    private fun checkAndShow(context: Context) {
        // 检查是否允许亮屏弹卡
        if (!ScreenState.canShowOnUserWake(context)) return

        val groups = MemoStore.load(context)
        val now = System.currentTimeMillis()
        val hasAvailable = groups.any { g ->
            !Prefs.isGroupSkipped(context, g.id) &&
                    g.isStarted(groups, now) &&
                    g.allCardsRecursive(groups).any { it.isDue(now) || it.isNew }
        }
        if (!hasAvailable) return

        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "MemoRecite::WakeLock"
            )
            wl.acquire(3000L)
            wl.release()
        } catch (_: Exception) {}

        val displayIntent = Intent(context, MemoDisplayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        var launched = false
        try {
            context.startActivity(displayIntent)
            launched = true
        } catch (_: Exception) {}

        // Android 10+ 后台启动 Activity 概率被拦截，通过 AlarmReceiver 的 FullScreen Notification 强行唤醒屏幕弹窗
        if (!launched || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val alarmIntent = Intent(context, MemoAlarmReceiver::class.java).apply {
                    putExtra("from_user_wake", true)
                }
                context.sendBroadcast(alarmIntent)
            } catch (_: Exception) {}
        }
    }
}
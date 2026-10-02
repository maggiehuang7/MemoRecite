package com.example.memorecite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        App.resetForeground()
        Prefs.clearPause(context)
        AlarmScheduler.scheduleNext(context)
    }

    private fun handleScreenOn(context: Context) {
        handler.postDelayed({
            checkAndShow(context)
        }, 200L)
    }

    private fun checkAndShow(context: Context) {
        if (Prefs.isInQuietHours(context)) return
        if (!ScreenState.canShowOnUserWake(context)) return

        val groups = MemoStore.load(context)
        val now = System.currentTimeMillis()

        // 🟢 递归查找所有已开始的组有没有可弹卡片
        val hasAvailable = groups.any { g ->
            g.isStarted(groups, now) &&
                    g.cards.isNotEmpty() &&
                    g.cards.any { it.isDue(now) || it.isNew }
        }
        if (!hasAvailable) return

        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
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
        try {
            context.startActivity(displayIntent)
        } catch (_: Exception) {}
    }
}
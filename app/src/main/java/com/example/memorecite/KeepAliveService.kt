package com.example.memorecite

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "keep_alive_channel"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_NAME = "后台运行"
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val SELF_WAKEUP_REQUEST = 8888
        private const val SELF_WAKEUP_INTERVAL_MS = 15 * 60 * 1000L  // 15 分钟

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                try { context.startService(intent) } catch (_: Exception) {}
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }

        /** 🟢 自我唤醒：每 15 分钟拉起自己一次（即使被杀也能恢复） */
        fun scheduleSelfWakeup(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context,
                SELF_WAKEUP_REQUEST,
                Intent(context, SelfWakeupReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + SELF_WAKEUP_INTERVAL_MS

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
    }

    private val screenStateReceiver = ScreenStateReceiver()

    private val handler = Handler(Looper.getMainLooper())
    private val heartbeatTask = object : Runnable {
        override fun run() {
            try {
                AlarmScheduler.scheduleNext(this@KeepAliveService)
            } catch (_: Exception) {}
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, GroupListActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MemoRecite")
            .setContentText("正在后台监控卡片复习提醒")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        registerScreenReceiver()

        // 启动心跳 + 自我唤醒
        AlarmScheduler.scheduleNext(this)
        handler.post(heartbeatTask)
        scheduleSelfWakeup(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            AlarmScheduler.scheduleNext(this)
        } catch (_: Exception) {}
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(heartbeatTask)
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}

        // 自动重启：立即重启 + 设置下一次自我唤醒
        try {
            val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
            val pi = PendingIntent.getService(
                this, 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC, System.currentTimeMillis() + 1000L, pi)
        } catch (_: Exception) {}
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
            val pi = PendingIntent.getService(
                this, 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC, System.currentTimeMillis() + 1000L, pi)
        } catch (_: Exception) {}
        super.onTaskRemoved(rootIntent)
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.deleteNotificationChannel(CHANNEL_ID)

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持后台运行"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                vibrationPattern = null
            }
            nm.createNotificationChannel(channel)
        }
    }
}
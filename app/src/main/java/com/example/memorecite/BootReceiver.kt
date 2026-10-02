package com.example.memorecite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {

                // 1. 重新安排闹钟
                AlarmScheduler.scheduleNext(context)

                // 2. 启动前台服务
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(
                            Intent(context, KeepAliveService::class.java)
                        )
                    } else {
                        context.startService(
                            Intent(context, KeepAliveService::class.java)
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
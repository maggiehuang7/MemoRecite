package com.example.memorecite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 自我唤醒接收器
 * 每 15 分钟被 AlarmManager 拉起一次，确保 KeepAliveService 一直在运行
 */
class SelfWakeupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 重启 KeepAliveService
        try {
            KeepAliveService.start(context)
        } catch (_: Exception) {}
    }
}
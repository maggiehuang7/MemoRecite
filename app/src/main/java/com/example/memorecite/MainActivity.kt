package com.example.memorecite

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etContent: EditText
    private lateinit var etInterval: EditText

    // 仅用于回显输入框内容，不动 MemoStore / Prefs 的卡片数据
    private val spName = "memo_quick_input"
    private val keyQuickContent = "quick_content"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etContent = findViewById(R.id.etContent)
        etInterval = findViewById(R.id.etInterval)

        // 回显间隔：用 Prefs 里已有的 firstDelay 字段
        etInterval.setText(Prefs.getFirstDelayMinutes(this).toString())

        // 回显输入框内容
        val sp = getSharedPreferences(spName, Context.MODE_PRIVATE)
        etContent.setText(sp.getString(keyQuickContent, ""))

        // 开始
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val intervalStr = etInterval.text.toString().trim()
            val intervalMin = intervalStr.toLongOrNull() ?: 1L
            if (intervalMin < 1) {
                Toast.makeText(this, "间隔至少 1 分钟", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sp.edit().putString(keyQuickContent, etContent.text.toString()).apply()
            Prefs.saveFirstDelay(this, intervalMin)
            AlarmScheduler.scheduleNext(this)
            Toast.makeText(this, "已开启提醒，间隔 $intervalMin 分钟", Toast.LENGTH_SHORT).show()
        }

        // 停止
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            AlarmScheduler.cancel(this)
            Toast.makeText(this, "已停止提醒", Toast.LENGTH_SHORT).show()
        }

        // 立即测试显示
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            sp.edit().putString(keyQuickContent, etContent.text.toString()).apply()
            startActivity(Intent(this, MemoDisplayActivity::class.java))
        }

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        // 1) 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }

        // 2) 精确闹钟（Android 12+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {}
            }
        }

        // 3) 全屏 Intent（Android 14+）——用反射，避免 SDK 版本问题
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val nm = getSystemService(NotificationManager::class.java)
                val canUse = NotificationManager::class.java
                    .getMethod("canUseFullScreenIntent")
                    .invoke(nm) as Boolean
                if (!canUse) {
                    startActivity(
                        Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT")
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
            } catch (_: Throwable) {
                // 反射失败即忽略
            }
        }
    }
}
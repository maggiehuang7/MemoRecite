package com.example.memorecite

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    const val REQUEST_CODE_NOTIFICATION = 2001

    fun checkAndRequestNotificationPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    activity, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATION
                )
                return false
            }
        }
        return true
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun hasBatteryOptimizationExemption(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else true
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                context.startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ))
            } catch (_: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                } catch (_: Exception) {}
            }
        }
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                context.startActivity(Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ))
            } catch (_: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {}
            }
        }
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.startActivity(Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                ))
            } catch (_: Exception) {}
        }
    }

    fun openAutoStartSettings(context: Context): Boolean {
        val intents = arrayOf(
            // Xiaomi / Redmi
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            // Huawei / Honor
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            // OPPO / Realme
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.privacypermissionsentry", "com.coloros.privacypermissionsentry.PermissionTopActivity")),
            // Vivo / iQOO
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // Meizu
            Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"))
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                continue
            }
        }

        // Fallback: App Details
        try {
            val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appInfoIntent)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun showBackgroundPermissionGuideDialog(context: Context) {
        val overlayOk = hasOverlayPermission(context)
        val batteryOk = hasBatteryOptimizationExemption(context)
        val exactAlarmOk = hasExactAlarmPermission(context)

        val options = arrayOf(
            "${if (overlayOk) "✅" else "❌"} 1. 悬浮窗/显示在其他应用上 (实现锁屏/后台弹卡)",
            "${if (batteryOk) "✅" else "❌"} 2. 忽略电池优化 (防止系统后台杀进程)",
            "${if (exactAlarmOk) "✅" else "❌"} 3. 精确闹钟权限 (确保定时复习准时触发)",
            "🚀 4. 自启动/后台弹出界面 (小米/华为/OPPO/vivo等系统必开)",
            "ℹ️ 应用详情页 (检查通知权限等)"
        )

        AlertDialog.Builder(context)
            .setTitle("🛡️ 后台运行与自动弹卡权限设置")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestOverlayPermission(context)
                    1 -> requestBatteryOptimizationExemption(context)
                    2 -> requestExactAlarmPermission(context)
                    3 -> {
                        val ok = openAutoStartSettings(context)
                        if (!ok) {
                            Toast.makeText(context, "请在系统设置中找到本应用并允许自启动和后台弹出", Toast.LENGTH_LONG).show()
                        }
                    }
                    4 -> {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
            }
            .setPositiveButton("完成", null)
            .show()
    }
}

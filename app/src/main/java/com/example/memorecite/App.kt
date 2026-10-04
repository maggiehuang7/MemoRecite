package com.example.memorecite

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle

class App : Application() {

    private var startedCount = 0

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // 🟢 App 启动时应用已保存的语言
        val savedLang = base.getSharedPreferences("memo_prefs", Context.MODE_PRIVATE)
            .getString("app_language", null)
        if (savedLang != null) {
            LocaleHelper.setLanguage(savedLang)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 🟢 进程初始化时重置前台及残留暂停状态
        resetForeground()
        Prefs.clearPause(this)

        try { KeepAliveService.start(this) } catch (_: Exception) {}
        try { AlarmScheduler.scheduleNext(this) } catch (_: Exception) {}

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {

            override fun onActivityStarted(activity: Activity) {
                if (activity is MemoDisplayActivity) return
                startedCount++
                if (startedCount == 1) {
                    isForeground = true
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity is MemoDisplayActivity) return
                startedCount--
                if (startedCount <= 0) {
                    startedCount = 0
                    isForeground = false
                    Prefs.clearPause(applicationContext)
                    try { KeepAliveService.start(applicationContext) } catch (_: Exception) {}
                    try { AlarmScheduler.scheduleNext(applicationContext) } catch (_: Exception) {}
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    companion object {
        @Volatile
        var isForeground: Boolean = false
            private set

        private const val PAUSE_MILLIS = 30 * 60 * 1000L

        fun resetForeground() { isForeground = false }
    }
}
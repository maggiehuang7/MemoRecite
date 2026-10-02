package com.example.memorecite

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 全局 Application：监听所有 Activity 生命周期
 * 用于判断"用户是否正在 App 里"
 */
class MemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                AppState.visibleActivityCount++
            }

            override fun onActivityStopped(activity: Activity) {
                AppState.visibleActivityCount =
                    (AppState.visibleActivityCount - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
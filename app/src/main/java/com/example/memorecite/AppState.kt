package com.example.memorecite

/**
 * 全局状态：记录当前 App 是否在前台
 */
object AppState {
    @Volatile
    var visibleActivityCount: Int = 0

    val isForeground: Boolean
        get() = visibleActivityCount > 0
}
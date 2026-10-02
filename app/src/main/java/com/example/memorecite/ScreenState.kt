package com.example.memorecite

import android.app.KeyguardManager
import android.content.Context

object ScreenState {

    private const val PREFS = "screen_state"
    private const val KEY_LAST_CLOSE_TIME = "last_close_time"

    /** 卡片关闭后冷却：30 分钟 */
    const val CLOSE_COOLDOWN = 30 * 60 * 1000L

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 🟢 设备是否"锁定"（未真正解锁）
     *
     * 用 isDeviceLocked()：
     * - 锁屏界面 → true
     * - 滑到 PIN/图案输入界面 → **仍为 true** ✅
     * - 真正输入密码解锁后 → false
     */
    fun isLocked(ctx: Context): Boolean {
        return try {
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.isDeviceLocked
        } catch (_: Exception) {
            false
        }
    }

    // ========== 冷却 ==========

    fun getLastCloseTime(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LAST_CLOSE_TIME, 0L)

    fun setLastCloseTime(ctx: Context, time: Long) {
        prefs(ctx).edit().putLong(KEY_LAST_CLOSE_TIME, time).apply()
    }

    fun clearCooldown(ctx: Context) {
        prefs(ctx).edit().remove(KEY_LAST_CLOSE_TIME).apply()
    }

    fun cooldownRemaining(ctx: Context): Long {
        val t = getLastCloseTime(ctx)
        if (t == 0L) return 0
        val elapsed = System.currentTimeMillis() - t
        return (CLOSE_COOLDOWN - elapsed).coerceAtLeast(0)
    }

    // ========== 弹卡判断 ==========

    /**
     * 🟢 用户主动亮屏 → 允许弹卡
     */
    fun canShowOnUserWake(ctx: Context): Boolean {
        if (!isLocked(ctx)) return false

        // 无条件清除所有障碍
        App.resetForeground()
        Prefs.clearPause(ctx)
        clearCooldown(ctx)

        return true
    }

    /**
     * 🟢 自动触发（闹钟）→ 必须冷却已过
     */
    fun canAutoShow(ctx: Context): Boolean {
        if (!isLocked(ctx)) return false

        App.resetForeground()

        if (Prefs.isPaused(ctx)) return false
        if (cooldownRemaining(ctx) > 0) return false

        return true
    }
}
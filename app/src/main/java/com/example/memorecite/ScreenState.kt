package com.example.memorecite

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

object ScreenState {

    private const val PREFS = "screen_state"
    private const val KEY_LAST_CLOSE_TIME = "last_close_time"

    /** 卡片关闭后冷却：30 分钟 */
    const val CLOSE_COOLDOWN = 30 * 60 * 1000L

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isLocked(ctx: Context): Boolean {
        return try {
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.isDeviceLocked
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 🟢 判断手机当前是否正在解锁使用中（屏幕亮着且已解锁，如正在使用其他 App）
     */
    fun isPhoneInActiveUse(ctx: Context): Boolean {
        return try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            pm.isInteractive && !km.isKeyguardLocked
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
     * 🟢 规则 1：用户主动亮屏（手动摁开屏幕） → 只要有到期卡片，立刻弹卡！
     */
    @Suppress("UNUSED_PARAMETER")
    fun canShowOnUserWake(ctx: Context): Boolean {
        if (App.isForeground) return false
        if (MemoDisplayActivity.isShowing) return false
        return true
    }

    /**
     * 🟢 规则 2 & 3：后台闹钟自动触发判断
     * - 规则 2：30 分钟熄屏冷却期内不自动弹卡
     * - 规则 3：用户在手机上使用其他 App 时（解锁使用中），完全不弹卡！
     */
    fun canAutoShow(ctx: Context): Boolean {
        if (App.isForeground) return false
        if (MemoDisplayActivity.isShowing) return false
        if (Prefs.isPaused(ctx)) return false
        if (isPhoneInActiveUse(ctx)) return false // 用户正在使用其他 App，完全不弹卡！
        if (cooldownRemaining(ctx) > 0) return false // 30 分钟熄屏冷却期内不自动弹卡
        return true
    }
}
package com.example.memorecite

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "memo_prefs"
    private const val KEY_INTERVALS = "intervals"
    private const val KEY_FIRST_DELAY = "first_delay"
    private const val KEY_SKIP_PREFIX = "skip_"
    private const val KEY_PERMISSION_PROMPTED = "permission_prompted"
    private const val KEY_PAUSE_UNTIL = "pause_until"

    private const val KEY_QUIET_ENABLED = "quiet_enabled"
    private const val KEY_QUIET_START_HOUR = "quiet_start_hour"
    private const val KEY_QUIET_START_MIN = "quiet_start_min"
    private const val KEY_QUIET_END_HOUR = "quiet_end_hour"
    private const val KEY_QUIET_END_MIN = "quiet_end_min"
    private const val KEY_DAILY_NEW_TARGET = "daily_new_target"

    private const val SKIP_COOLDOWN_MS = 30 * 60 * 1000L

    fun getDailyNewTarget(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DAILY_NEW_TARGET, 80)
    }

    fun saveDailyNewTarget(context: Context, target: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAILY_NEW_TARGET, target).apply()
    }

    val DEFAULT_INTERVALS: List<Long> = listOf(
        5L, 30L, 720L, 1440L, 2880L, 5760L, 10080L, 21600L, 43200L, 86400L
    )

    fun getIntervals(context: Context): List<Long> {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_INTERVALS, null) ?: return DEFAULT_INTERVALS
        val arr = s.split(",").mapNotNull { it.trim().toLongOrNull() }
        return if (arr.isEmpty()) DEFAULT_INTERVALS else arr
    }

    fun saveIntervals(context: Context, intervals: List<Long>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_INTERVALS, intervals.joinToString(",")).apply()
    }

    fun getFirstDelayMinutes(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_FIRST_DELAY, 1L)
    }

    fun saveFirstDelay(context: Context, minutes: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_FIRST_DELAY, minutes).apply()
    }

    fun skipGroup(context: Context, groupId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SKIP_PREFIX + groupId, System.currentTimeMillis()).apply()
    }

    fun isGroupSkipped(context: Context, groupId: String): Boolean {
        val t = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SKIP_PREFIX + groupId, 0L)
        return System.currentTimeMillis() - t < SKIP_COOLDOWN_MS
    }

    fun clearSkip(context: Context, groupId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_SKIP_PREFIX + groupId).apply()
    }

    fun isPermissionPrompted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSION_PROMPTED, false)
    }

    fun setPermissionPrompted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PERMISSION_PROMPTED, true).apply()
    }

    fun resetPermissionPrompted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_PERMISSION_PROMPTED).apply()
    }

    fun pauseFor(context: Context, millis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_PAUSE_UNTIL, System.currentTimeMillis() + millis).apply()
    }

    fun isPaused(context: Context): Boolean {
        val until = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PAUSE_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun clearPause(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_PAUSE_UNTIL).apply()
    }

    // ========== 免打扰时段 ==========

    fun isQuietEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_QUIET_ENABLED, true)
    }

    fun setQuietEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_QUIET_ENABLED, enabled).apply()
    }

    fun getQuietTime(context: Context): IntArray {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return intArrayOf(
            sp.getInt(KEY_QUIET_START_HOUR, 0),
            sp.getInt(KEY_QUIET_START_MIN, 0),
            sp.getInt(KEY_QUIET_END_HOUR, 6),
            sp.getInt(KEY_QUIET_END_MIN, 0)
        )
    }

    fun setQuietTime(context: Context, startHour: Int, startMin: Int, endHour: Int, endMin: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_QUIET_START_HOUR, startHour)
            .putInt(KEY_QUIET_START_MIN, startMin)
            .putInt(KEY_QUIET_END_HOUR, endHour)
            .putInt(KEY_QUIET_END_MIN, endMin)
            .apply()
    }

    fun isInQuietHours(context: Context): Boolean {
        if (!isQuietEnabled(context)) return false

        val t = getQuietTime(context)
        val startMin = t[0] * 60 + t[1]
        val endMin = t[2] * 60 + t[3]

        val cal = java.util.Calendar.getInstance()
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                cal.get(java.util.Calendar.MINUTE)

        return if (startMin <= endMin) {
            nowMin in startMin until endMin
        } else {
            nowMin >= startMin || nowMin < endMin
        }
    }

    fun quietHoursRemaining(context: Context): Long {
        if (!isInQuietHours(context)) return 0
        val endTime = getQuietEndTime(context)
        return (endTime - System.currentTimeMillis()).coerceAtLeast(0)
    }

    /**
     * 🟢 计算"下次免打扰结束"的时间戳
     */
    fun getQuietEndTime(context: Context): Long {
        val t = getQuietTime(context)
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, t[2])
        cal.set(java.util.Calendar.MINUTE, t[3])
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)

        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
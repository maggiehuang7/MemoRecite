package com.example.memorecite

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {

    /** 语言代码存储 key */
    private const val PREF_KEY = "app_language"
    const val DEFAULT_LANG = "zh"

    /** 支持的语言列表（想加语言，在这里加一行即可） */
    val SUPPORTED = listOf(
        Language("zh", "中文", "🇨🇳"),
        Language("en", "English", "🇬🇧"),
        Language("ja", "日本語", "🇯🇵")
    )

    data class Language(val code: String, val name: String, val flag: String)

    /** 获取当前语言代码 */
    fun getCurrent(ctx: Context): String {
        val fromPrefs = ctx.getSharedPreferences("memo_prefs", Context.MODE_PRIVATE)
            .getString(PREF_KEY, null)
        if (fromPrefs != null) return fromPrefs

        // 没存过 → 用系统语言
        val sysLang = LocaleListCompat.getDefault()
        val sys = sysLang.toLanguageTags().split("-", ",").firstOrNull() ?: DEFAULT_LANG
        return if (SUPPORTED.any { it.code == sys }) sys else DEFAULT_LANG
    }

    /** 获取当前语言显示名 */
    fun getCurrentName(ctx: Context): String {
        val code = getCurrent(ctx)
        return SUPPORTED.firstOrNull { it.code == code }?.name ?: "中文"
    }

    /** 应用语言（立即生效） */
    fun setLanguage(langCode: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(langCode)
        )
    }

    /** 保存到本地（App 重启后恢复） */
    fun saveLanguage(ctx: Context, langCode: String) {
        ctx.getSharedPreferences("memo_prefs", Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, langCode).apply()
    }
}
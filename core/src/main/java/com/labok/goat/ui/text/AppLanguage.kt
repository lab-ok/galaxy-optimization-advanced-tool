package com.labok.goat.ui.text

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 앱 언어 선택(시스템 기본·한국어·English). Android 13부터는 시스템의 앱별 언어(LocaleManager)에 맡기고,
 * 그 아래(Android 12)에서는 저장해 둔 언어로 액티비티·앱 컨텍스트를 감싼다([wrap]).
 * 언어 목록의 국기는 소장님 지시(11-11 "언어설정할때는 그 나라의 국기도 표시해줘").
 */
object AppLanguage {
    /** [tag]가 빈 문자열이면 시스템 기본. */
    enum class Option(val tag: String, val flag: String, val label: String) {
        SYSTEM("", "🌐", ""),
        KOREAN("ko", "🇰🇷", "한국어"),
        ENGLISH("en", "🇺🇸", "English")
    }

    private const val PREF = "app_language"

    fun current(context: Context): Option {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language.orEmpty()
        } else {
            saved(context)
        }
        return Option.entries.firstOrNull { it.tag == tag && tag.isNotEmpty() } ?: Option.SYSTEM
    }

    /** 언어를 바꾼다. Android 13+는 시스템이 화면을 다시 만들고, 그 아래는 호출한 쪽이 액티비티를 다시 띄운다. */
    fun set(context: Context, option: Option) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("tag", option.tag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (option.tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(option.tag)
        }
    }

    private fun saved(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("tag", "").orEmpty()

    /** Android 12 이하: 저장한 언어로 컨텍스트를 감싼다(액티비티·앱의 attachBaseContext에서 부른다). 13+는 그대로 돌려준다. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = runCatching { saved(base) }.getOrDefault("")
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }
}

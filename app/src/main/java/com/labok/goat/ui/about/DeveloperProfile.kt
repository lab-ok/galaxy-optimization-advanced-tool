package com.labok.goat.ui.about

import com.labok.goat.AppInfo
import com.labok.goat.R
import com.labok.goat.ui.text.Copy

/** 정보 화면에 표시하는 제품·개발자 정보. 표시 문구는 이 파일 한곳에서 고친다. */
object DeveloperProfile {
    const val PRODUCT = AppInfo.NAME
    const val TAGLINE = AppInfo.FULL_NAME

    /** 앱 정보 소개: 한 줄 머리말과 짧은 특징 세 줄. 문단으로 붙이지 않고 줄마다 띄워 보여 준다. */
    val HEADLINE: String get() = Copy.text(R.string.about_headline)
    val HIGHLIGHTS: List<String>
        get() = listOf(
            Copy.text(R.string.about_highlight_display),
            Copy.text(R.string.about_highlight_no_root),
            Copy.text(R.string.about_highlight_persist)
        )
    const val CONTACT_EMAIL = "lala5bok@gmail.com"
    const val COPYRIGHT = "© 2026 lala5bok · GPL-3.0"
    val LICENSE: String get() = Copy.text(R.string.about_license_value)
    val LICENSE_NOTE: String get() = Copy.text(R.string.about_license_note)
}

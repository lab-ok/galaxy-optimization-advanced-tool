package com.labok.goat.ui.about

import com.labok.goat.AppInfo

/** 정보 화면에 표시하는 제품·개발자 정보. 표시 문구는 이 파일 한곳에서 고친다. */
object DeveloperProfile {
    const val PRODUCT = AppInfo.NAME
    const val TAGLINE = AppInfo.FULL_NAME

    /** 앱 정보 소개: 한 줄 머리말과 짧은 특징 세 줄. 문단으로 붙이지 않고 줄마다 띄워 보여 준다. */
    const val HEADLINE = "갤럭시를 내 손에 맞게"
    val HIGHLIGHTS = listOf(
        "화면 배율과 표시를 원하는 대로",
        "ROOT 없이 안전하게",
        "재부팅해도 그대로"
    )
    const val CONTACT_EMAIL = "lala5bok@gmail.com"
    const val COPYRIGHT = "© 2026 lala5bok · GPL-3.0"
    const val LICENSE = "GPL-3.0 (오픈소스)"
    const val LICENSE_NOTE = "누구나 무료로 쓰고 고칠 수 있습니다. 고친 앱을 배포할 때는 소스도 GPL-3.0으로 공개해야 하며, 그러기 어려우면 상용 라이선스를 문의하세요."
}

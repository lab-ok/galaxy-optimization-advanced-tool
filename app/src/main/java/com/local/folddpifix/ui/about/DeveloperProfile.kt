package com.local.folddpifix.ui.about

import com.local.folddpifix.AppInfo

/** 정보 화면에 표시하는 제품·개발자 정보. 표시 문구는 이 파일 한곳에서 고친다. */
object DeveloperProfile {
    const val PRODUCT = AppInfo.NAME
    const val TAGLINE = "외부·내부 화면 표시 크기 일치"
    const val DESCRIPTION = "외부 화면 DPI를 기준으로 내부 화면 DPI를 계산해 두 화면의 표시 크기를 맞추고, 재부팅 후에도 자동으로 다시 적용합니다."
    const val CONTACT_EMAIL = "lala5bok@gmail.com"
    const val COPYRIGHT = "© 2026 lala5bok · GPL-3.0"
    const val LICENSE = "GPL-3.0 (오픈소스)"
    const val LICENSE_NOTE = "누구나 무료로 쓰고 고칠 수 있습니다. 고친 앱을 배포할 때는 소스도 GPL-3.0으로 공개해야 하며, 그러기 어려우면 상용 라이선스를 문의하세요."
}

package com.local.folddpifix.domain

/**
 * 앱별 화면 크기(삼성 앱 화면 크게/작게) 규칙. 실기기(One UI 9) services.jar에서 확인한 동작을 그대로 옮긴다.
 * - 받는 값: [STEPS]와 0(기본, 앱별 설정 지움). 그 밖의 값은 시스템이 거절한다.
 * - 읽기 함수는 320을 360으로 돌려준다. 실제 적용은 320이므로 FoldFit이 정한 값으로 바로잡아 보여 준다.
 */
object AppDensityPolicy {
    /** MultiTaskingAppCompatDensityOverrides.SUPPORTED_VALUES */
    val STEPS = listOf(320, 360, 420, 450, 480, 510)

    fun isAllowed(dpi: Int) = dpi == 0 || dpi in STEPS

    /**
     * 알림 버튼 한 번에 갈 다음 값. [base]는 지금 실제 크기(기본이면 시스템 DPI), [step]은 -1(작게)·0(기본)·1(크게).
     * 더 갈 곳이 없으면 null.
     */
    fun next(base: Int, step: Int): Int? = when {
        step == 0 -> 0
        step < 0 -> STEPS.lastOrNull { it < base }
        else -> STEPS.firstOrNull { it > base }
    }

    /** 읽은 값([reported])을 보여 줄 값으로. FoldFit이 320으로 정해 두었는데 360으로 읽히면 320. */
    fun shown(reported: Int, mine: Int?): Int = if (reported == 360 && mine == 320) 320 else reported
}

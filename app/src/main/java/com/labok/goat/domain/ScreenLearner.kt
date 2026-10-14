package com.labok.goat.domain

import com.labok.goat.data.settings.SettingsRepository

/**
 * 새 화면(패널)을 기억할지 판단한다. 접거나 펴는 도중에는 중간 해상도가 잠깐 읽힐 수 있어서,
 * 처음 보는 해상도는 [STABLE_MS] 이상 간격을 두고 두 번 같은 값이 읽혀야 기억한다.
 * 이미 아는 화면은 바로 갱신한다.
 *
 * 접는 폰이 아니면([singleScreen]) 화면은 하나뿐이라 지금 화면 하나만 기억한다. 해상도(`wm size`·삼성 해상도 설정)를
 * 바꿀 때마다 새 화면으로 쌓이면 가장 작은 쪽·큰 쪽이 외부·내부로 잘못 정해져 화면마다 다른 목표가 나온다.
 */
class ScreenLearner(
    private val settings: SettingsRepository,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val singleScreen: () -> Boolean = { false }
) {
    fun observe(screen: ScreenInfo?, source: String) {
        if (screen == null || !ScreenPolicy.isPlausible(screen)) return
        synchronized(lock) {
            if (singleScreen()) {
                // 해상도 낮추기 비율(scale)은 저장하지 않는 값이라 빼고 비교한다
                val only = screen.copy(scale = 1f)
                val before = settings.screens
                if (before == listOf(only)) return
                settings.screens = listOf(only)
                candidate = null
                val cleaned = if (before.size > 1) " · 이전 기록 ${before.size}개를 정리" else ""
                log("화면 학습($source): ${screen.key} 보고 ${"%.1f".format(screen.ppi)}ppi · 화면이 하나인 기기$cleaned")
                return
            }
            val known = settings.screens.any { it.key == screen.key }
            if (!known) {
                val now = clock()
                val c = candidate
                if (c == null || c.first.key != screen.key || now - c.second < STABLE_MS) {
                    if (c == null || c.first.key != screen.key) candidate = screen to now
                    return
                }
                candidate = null
            }
            if (settings.rememberScreen(screen)) {
                log(
                    "화면 ${if (known) "갱신" else "학습"}($source): ${screen.key} " +
                        "보고 ${"%.1f".format(screen.ppi)}ppi ${"%.2f".format(screen.diagonalInch)}\" · 기억한 화면 ${settings.screens.size}개"
                )
            }
        }
    }

    /** 꺼진 디스플레이(ID ≠ 0)가 보고한 값. 해상도가 이미 학습된 화면일 때만 기록한다. */
    fun observeInactive(displayId: Int, screen: ScreenInfo?) {
        if (screen == null || !ScreenPolicy.isPlausible(screen)) return
        if (settings.screens.none { it.key == screen.key }) return
        if (settings.rememberInactivePpi(screen.key, screen.ppi)) {
            log("비활성 디스플레이 보고값 기록: -d $displayId ${screen.key} ${"%.1f".format(screen.ppi)}ppi")
        }
    }

    companion object {
        const val STABLE_MS = 800L

        /** 처음 보는 화면을 한 번 읽어 두고 확인을 기다리는 중이면 true. 호출하는 쪽은 잠시 뒤 다시 확인해야 한다. */
        val hasPendingCandidate: Boolean get() = candidate != null
        private val lock = Any()

        /** 프로세스 안에서 공유하는 후보(서비스·화면·작업이 같은 후보를 본다). */
        @Volatile
        private var candidate: Pair<ScreenInfo, Long>? = null

        internal fun resetForTest() {
            candidate = null
        }
    }
}

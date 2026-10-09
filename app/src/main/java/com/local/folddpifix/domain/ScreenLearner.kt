package com.local.folddpifix.domain

import com.local.folddpifix.data.settings.SettingsRepository

/**
 * 새 화면(패널)을 기억할지 판단한다. 접거나 펴는 도중에는 중간 해상도가 잠깐 읽힐 수 있어서,
 * 처음 보는 해상도는 [STABLE_MS] 이상 간격을 두고 두 번 같은 값이 읽혀야 기억한다.
 * 이미 아는 화면은 바로 갱신한다.
 */
class ScreenLearner(
    private val settings: SettingsRepository,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(screen: ScreenInfo?, source: String) {
        if (screen == null || !ScreenPolicy.isPlausible(screen)) return
        synchronized(lock) {
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

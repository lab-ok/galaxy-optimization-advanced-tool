package com.local.folddpifix.domain

/** DPI 값 검증과 "지금 무엇을 해야 하는가" 판단. Android 의존성이 없어 단위 테스트로 검증한다. */
object DpiPolicy {
    /** 이보다 작으면 글자가 지나치게 커지고, 크면 터치가 어려울 만큼 작아진다. */
    const val MIN_DPI = 240
    const val MAX_DPI = 640

    /** 같은 창(window) 안에서 허용하는 최대 적용 횟수. Samsung과 서로 덮어쓰는 무한 반복을 막는다. */
    const val MAX_APPLIES_PER_WINDOW = 5
    const val APPLY_WINDOW_MS = 60_000L

    /** 부팅 뒤 확인 시점(초). 0초 즉시, 5·15·30초 재확인, 60초 최종 확인. */
    val BOOT_CHECK_OFFSETS_SEC = listOf(0L, 5L, 15L, 30L, 60L)

    sealed interface Validation {
        data class Ok(val dpi: Int) : Validation
        data class Error(val message: String) : Validation
    }

    fun validate(input: String?): Validation {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return Validation.Error("DPI를 입력하세요")
        val dpi = text.toIntOrNull() ?: return Validation.Error("숫자만 입력하세요")
        if (dpi < MIN_DPI || dpi > MAX_DPI) {
            return Validation.Error("$MIN_DPI~$MAX_DPI 범위로 입력하세요")
        }
        return Validation.Ok(dpi)
    }

    fun isValid(dpi: Int): Boolean = dpi in MIN_DPI..MAX_DPI

    enum class Decision { EXTERNAL_CHANGE, BLOCKED, SKIP_DISABLED, NO_PERMISSION, NO_TARGET, UNKNOWN_CURRENT, ALREADY_OK, RATE_LIMITED, APPLY }

    fun decide(
        automatic: Boolean,
        autoEnabled: Boolean,
        hasPermission: Boolean,
        target: Int?,
        current: Int?,
        recentApplyTimes: List<Long>,
        now: Long,
    ): Decision {
        if (automatic && !autoEnabled) return Decision.SKIP_DISABLED
        if (!hasPermission) return Decision.NO_PERMISSION
        if (target == null || !isValid(target)) return Decision.NO_TARGET
        if (current == null) return Decision.UNKNOWN_CURRENT
        if (current == target) return Decision.ALREADY_OK
        // 반복 방지는 자동 적용에만 건다. 사용자가 직접 바꾸는 것은 막지 않는다.
        if (automatic && recentAppliesInWindow(recentApplyTimes, now) >= MAX_APPLIES_PER_WINDOW) return Decision.RATE_LIMITED
        return Decision.APPLY
    }

    fun recentAppliesInWindow(times: List<Long>, now: Long): Int =
        times.count { now - it in 0 until APPLY_WINDOW_MS }

    /** 저장할 적용 기록. 창 밖의 오래된 기록은 버린다. */
    fun pruneApplyTimes(times: List<Long>, now: Long): List<Long> =
        times.filter { now - it in 0 until APPLY_WINDOW_MS }.takeLast(MAX_APPLIES_PER_WINDOW * 2)
}

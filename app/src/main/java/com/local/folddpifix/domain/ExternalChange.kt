package com.local.folddpifix.domain

import kotlin.math.roundToInt

/**
 * 앱이 아닌 곳(시스템 설정의 화면 크기, 다른 앱, adb 등)에서 DPI가 바뀐 것.
 * 앱은 화면마다 마지막으로 적용·확인한 값([SettingsRepository.appliedDpi])을 기억하고,
 * 지금 값이 그것과도 목표와도 다르면 외부 변경으로 본다. 이때는 자동으로 덮어쓰지 않고
 * 사용자가 '이 값을 기준으로' 또는 '앱 설정으로 복원'을 고를 때까지 기다린다.
 */
data class ExternalChange(
    val key: String,
    val role: ScreenPolicy.Role,
    /** 앱이 마지막으로 적용한 값(복원하면 돌아갈 값). */
    val from: Int,
    /** 바뀐 지금 값. */
    val to: Int,
    val timeMs: Long,
) {
    fun serialize() = "$key|${role.name}|$from|$to|$timeMs"

    companion object {
        fun parse(raw: String?): ExternalChange? {
            val p = raw?.split('|') ?: return null
            if (p.size != 5) return null
            val role = ScreenPolicy.Role.entries.find { it.name == p[1] } ?: return null
            return ExternalChange(p[0], role, p[2].toIntOrNull() ?: return null, p[3].toIntOrNull() ?: return null, p[4].toLongOrNull() ?: return null)
        }

        /**
         * 외부 변경 판정. [physicalIsReset]이 true면 기기 기본값으로 돌아간 것은 시스템 초기화(부팅 직후 등)로 보고
         * 외부 변경으로 치지 않는다(그대로 복원 대상).
         */
        fun detect(
            key: String,
            role: ScreenPolicy.Role?,
            current: Int?,
            physical: Int?,
            target: Int?,
            applied: Int?,
            physicalIsReset: Boolean,
            now: Long,
            /** 앱이 다른 화면에 적용한 값들. 화면이 바뀐 직후 다른 화면 값이 잠시 보이는 것은 외부 변경이 아니다. */
            otherApplied: Collection<Int> = emptyList(),
        ): ExternalChange? {
            if (role == null || current == null || applied == null || target == null) return null
            if (current == applied || current == target || current in otherApplied) return null
            if (physicalIsReset && current == physical) return null
            return ExternalChange(key, role, applied, current, now)
        }

        /**
         * '이 값을 기준으로'를 고르면 쓸 새 설정(외부 화면 DPI, 내부 화면 보정).
         * 외부 화면이 바뀌었으면 그 값이 그대로 기준이 된다. 내부 화면이 바뀌었으면 PPI 비율로 외부 기준을 거꾸로 계산하고,
         * 반올림으로 생기는 1~2 dpi 차이는 보정값으로 메워 내부 화면이 정확히 바뀐 값이 되게 한다.
         */
        fun adopt(change: ExternalChange, plan: DensityPlan): Pair<Int, Int>? {
            if (change.role == plan.reference) return change.to to plan.adjust
            val screens = plan.panels.map { it.screen }
            val ref = ScreenPolicy.screenFor(screens, plan.reference) ?: return null
            val active = screens.find { it.key == change.key } ?: return null
            val reference = ((change.to - plan.adjust) * ref.ppi / active.ppi).roundToInt()
            if (!DpiPolicy.isValid(reference)) return null
            val computed = plan.copy(referenceDpi = reference).targetFor(active).dpi
            return reference to plan.adjust + (change.to - computed)
        }
    }
}

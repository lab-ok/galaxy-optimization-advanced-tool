package com.local.folddpifix.domain

import android.content.Context
import com.local.folddpifix.data.display.DpiController
import com.local.folddpifix.data.display.DpiManager
import com.local.folddpifix.data.log.LogRepository
import com.local.folddpifix.data.settings.LastApply
import com.local.folddpifix.data.settings.SettingsRepository

/**
 * 부팅·접기/펼치기·수동 적용이 모두 거치는 공통 경로.
 * 현재 값 확인 → 목표와 다를 때만 적용 → 적용 결과와 검증을 기록한다.
 */
class DpiFixer(
    private val controller: DpiController,
    private val settings: SettingsRepository,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val learner = ScreenLearner(settings, log, clock)

    /**
     * @param physicalIsReset 기본값으로 돌아간 것을 시스템 초기화로 볼지(부팅·접기 경로는 true).
     *   DPI 변경 감지 경로에서는 false: 사용자가 시스템 설정에서 기본값을 고른 것일 수 있다.
     */
    fun ensure(reason: String, automatic: Boolean, physicalIsReset: Boolean = true): DpiPolicy.Decision = synchronized(lock) {
        val now = clock()
        val hasPermission = controller.hasPermission()
        val screen = controller.currentScreen()
        learner.observe(screen, reason)
        val effective = settings.plan()?.targetFor(screen)
        val target = effective?.dpi
        val where = effective?.role?.let { "${it.label} " }.orEmpty()
        val state = if (hasPermission) controller.read() else null
        if (hasPermission) log("[$reason] 상태 ${snapshot(screen)}")
        val blocked = effective?.blocked
        // 사용자가 직접 적용하면(슬라이더·지금 적용·복원) 기다리던 외부 변경은 끝난다.
        if (!automatic && settings.externalChange != null) settings.externalChange = null
        val external = if (automatic && hasPermission && screen != null) holdExternal(reason, screen.key, effective, state, physicalIsReset, now) else null
        if (external != null) return@synchronized DpiPolicy.Decision.EXTERNAL_CHANGE
        val decision = if (blocked != null && (automatic.not() || settings.autoRecover) && hasPermission) {
            DpiPolicy.Decision.BLOCKED
        } else DpiPolicy.decide(
            automatic = automatic,
            autoEnabled = settings.autoRecover,
            hasPermission = hasPermission,
            target = target,
            current = state?.current,
            recentApplyTimes = settings.applyTimes,
            now = now,
        )
        when (decision) {
            DpiPolicy.Decision.BLOCKED -> log("[$reason] ${where}적용 보류: $blocked")
            DpiPolicy.Decision.SKIP_DISABLED -> log("[$reason] 자동 복구 꺼짐, 건너뜀")
            DpiPolicy.Decision.NO_PERMISSION -> log("[$reason] WRITE_SECURE_SETTINGS 권한 없음, 적용하지 않음")
            DpiPolicy.Decision.NO_TARGET -> log("[$reason] 목표 DPI가 저장되지 않음")
            DpiPolicy.Decision.UNKNOWN_CURRENT -> log("[$reason] 현재 DPI를 읽지 못함")
            DpiPolicy.Decision.EXTERNAL_CHANGE -> Unit
            DpiPolicy.Decision.ALREADY_OK -> {
                log("[$reason] ${where}현재 ${state?.current} = 목표 $target, 정상")
                if (screen != null && target != null) settings.rememberApplied(screen.key, target)
            }
            DpiPolicy.Decision.RATE_LIMITED ->
                log("[$reason] 최근 1분 적용 ${DpiPolicy.MAX_APPLIES_PER_WINDOW}회 초과, 반복 방지로 중단")
            DpiPolicy.Decision.APPLY -> {
                log("[$reason] ${where}현재 ${state?.current} ≠ 목표 $target, 적용")
                effective?.note?.let { log("[$reason] $it") }
                val result = controller.apply(target!!)
                if (automatic) settings.applyTimes = DpiPolicy.pruneApplyTimes(settings.applyTimes + now, now)
                settings.lastApply = LastApply(result.success, now, "${where}${target} dpi · ${result.method}")
                if (result.success) {
                    screen?.let { settings.rememberApplied(it.key, target) }
                    log("[$reason] 적용 성공 $target (${result.method}) ${result.detail}")
                } else {
                    log("[$reason] 적용 실패: ${result.detail}")
                }
            }
        }
        if (decision != DpiPolicy.Decision.SKIP_DISABLED &&
            decision != DpiPolicy.Decision.NO_PERMISSION &&
            decision != DpiPolicy.Decision.NO_TARGET
        ) {
            ensureOtherDisplays(reason, screen, automatic)
        }
        decision
    }

    /**
     * Galaxy Fold는 외부·내부 화면이 디스플레이 번호(-d 0, -d 1)로 따로 있다.
     * 지금 켜진 화면 외의 디스플레이도 해상도로 어느 화면인지 확인되면 보정 DPI를 바로 적용한다.
     * 어느 화면인지 확인할 수 없는 디스플레이는 건드리지 않는다.
     */
    private fun ensureOtherDisplays(reason: String, active: ScreenInfo?, automatic: Boolean) {
        for (d in controller.displays()) {
            val seen = d.screen ?: continue
            if (d.id != DEFAULT_DISPLAY_ID && seen.key != active?.key) learner.observeInactive(d.id, seen)
        }
        val plan = settings.plan() ?: return
        for (d in controller.displays()) {
            val seen = d.screen ?: continue
            if (d.id == DEFAULT_DISPLAY_ID || seen.key == active?.key) continue
            // 해상도로 어느 화면인지 확인하고, 목표는 학습한 화면 정보(DensityPlan)로 계산한다.
            val screen = settings.screens.find { it.key == seen.key } ?: continue
            val t = plan.targetFor(screen)
            val role = t.role ?: continue
            val label = "[$reason] -d ${d.id} ${role.label}"
            if (settings.externalChange?.key == screen.key) {
                log("$label 외부 변경 선택 대기 중, 건너뜀")
                continue
            }
            if (t.blocked != null) {
                log("$label 적용 보류: ${t.blocked}")
                continue
            }
            if (d.readable && d.current == t.dpi) {
                log("$label 현재 ${d.current} = 목표 ${t.dpi}, 정상")
                settings.rememberApplied(screen.key, t.dpi)
                appliedUnverified.remove(screen.key)
                continue
            }
            if (!d.readable && appliedUnverified[screen.key] == t.dpi) continue
            val now = clock()
            if (automatic && DpiPolicy.recentAppliesInWindow(settings.applyTimes, now) >= DpiPolicy.MAX_APPLIES_PER_WINDOW) {
                log("$label 반복 방지로 적용하지 않음")
                continue
            }
            log("$label 현재 ${d.current ?: "?"} ≠ 목표 ${t.dpi}, 적용")
            val r = controller.applyTo(d.id, t.dpi)
            if (automatic) settings.applyTimes = DpiPolicy.pruneApplyTimes(settings.applyTimes + now, now)
            log(if (r.success) "$label 적용 성공 ${t.dpi} (${r.method}) ${r.detail}" else "$label 적용 실패: ${r.detail}")
            if (r.success) settings.rememberApplied(screen.key, t.dpi)
            if (r.success && !d.readable) appliedUnverified[screen.key] = t.dpi
        }
    }

    /**
     * 외부 변경이면 기록하고(처음 발견할 때만 로그) 그 변경을 돌려준다. 사용자가 고를 때까지 자동 적용을 멈춘다.
     * 기다리던 변경이 이미 해소됐으면(목표값이 됐거나 앱 값으로 돌아옴) 기록을 지운다.
     */
    private fun holdExternal(
        reason: String,
        key: String,
        effective: ScreenPolicy.Target?,
        state: com.local.folddpifix.data.display.DpiState?,
        physicalIsReset: Boolean,
        now: Long,
    ): ExternalChange? {
        val current = state?.current
        settings.externalChange?.let { pending ->
            if (pending.key != key) return null
            if (current == pending.to) return pending
            settings.externalChange = null
            log("[$reason] 외부 변경이 해소됨(현재 $current)")
        }
        val found = ExternalChange.detect(
            key, effective?.role, current, state?.physical, effective?.dpi,
            settings.appliedDpi[key], physicalIsReset, now,
            otherApplied = settings.appliedDpi.filterKeys { it != key }.values,
        ) ?: return null
        settings.externalChange = found
        log("[$reason] 외부 DPI 변경 감지: ${found.role.label} ${found.from} → ${found.to}. 사용자 선택을 기다림")
        return found
    }

    /**
     * 외부 변경에 대한 사용자 선택. [adopt]가 true면 바뀐 값을 새 기준으로 삼고, false면 앱 설정으로 복원한다.
     * 어느 쪽이든 마지막에 수동 적용으로 두 화면을 맞춘다.
     */
    fun resolveExternal(adopt: Boolean, reason: String): DpiPolicy.Decision = synchronized(lock) {
        val change = settings.externalChange
        if (change != null && adopt) {
            val plan = settings.plan()
            val next = plan?.let { ExternalChange.adopt(change, it) }
            if (next != null) {
                val (reference, adjust) = next
                log("[$reason] 외부 변경을 기준으로: 외부 ${settings.targetDpi} → $reference, 보정 ${settings.adjust} → $adjust")
                settings.targetDpi = reference
                settings.adjust = adjust
                settings.rememberApplied(change.key, change.to)
            } else {
                log("[$reason] 외부 변경 값(${change.to})으로 기준을 계산하지 못해 복원함")
            }
        } else if (change != null) {
            log("[$reason] 앱 설정으로 복원: ${change.role.label} ${change.to} → ${change.from}")
        }
        ensure(reason, automatic = false)
    }

    /** 자동 적용 없이 외부 변경만 확인한다(앱 화면을 열 때). */
    fun checkExternal(reason: String): ExternalChange? = synchronized(lock) {
        if (!controller.hasPermission()) return null
        val screen = controller.currentScreen() ?: return null
        val effective = settings.plan()?.targetFor(screen)
        holdExternal(reason, screen.key, effective, controller.read(), physicalIsReset = false, now = clock())
    }

    /** 로그용 한 줄 요약: 켜진 화면과 디스플레이 ID별 해상도·현재 밀도. */
    private fun snapshot(active: ScreenInfo?): String = buildString {
        append("켜짐=${active?.key ?: "?"}")
        controller.displays().forEach { d ->
            append(" | -d ${d.id} ${d.screen?.key ?: "?"} 기본${d.physical} 현재${d.current ?: "?"}")
        }
        append(" | 학습=${settings.screens.joinToString(",") { it.key }}")
    }

    /** 목표 DPI로 맞춰진 것이 확인된 화면 key 전체(켜진 화면 + 다른 디스플레이). */
    fun satisfiedScreenKeys(): Set<String> = synchronized(lock) {
        val keys = mutableSetOf<String>()
        satisfiedScreenKey()?.let(keys::add)
        val plan = settings.plan() ?: return keys
        if (!controller.hasPermission()) return keys
        for (d in controller.displays()) {
            val seen = d.screen ?: continue
            val screen = settings.screens.find { it.key == seen.key } ?: continue
            val t = plan.targetFor(screen)
            if (t.role == null) continue
            if ((d.readable && d.current == t.dpi) || (!d.readable && appliedUnverified[screen.key] == t.dpi)) {
                keys += screen.key
            }
        }
        keys
    }

    /** 지금 켜진 화면이 목표 DPI로 맞춰져 있으면 그 화면 key, 아니면 null. */
    fun satisfiedScreenKey(): String? = synchronized(lock) {
        if (!controller.hasPermission()) return null
        val screen = controller.currentScreen() ?: return null
        val target = settings.plan()?.targetFor(screen) ?: return null
        // 반대쪽 화면을 아직 모르면 보정값이 확정되지 않았으므로 확인으로 치지 않는다.
        if (target.role == null) return null
        if (controller.read().current == target.dpi) screen.key else null
    }

    companion object {
        private val lock = Any()
        private const val DEFAULT_DISPLAY_ID = 0

        /** 값을 읽을 수 없는(꺼진) 디스플레이에 이번 프로세스에서 적용한 값. 같은 값을 반복 적용하지 않는다. */
        private val appliedUnverified = mutableMapOf<String, Int>()

        fun from(context: Context): DpiFixer {
            val logRepo = LogRepository.from(context)
            return DpiFixer(DpiManager(context), SettingsRepository.from(context), logRepo::add)
        }
    }
}

package com.local.folddpifix.domain

import com.local.folddpifix.data.settings.SettingsRepository
import kotlin.math.hypot

/**
 * 패널의 물리 PPI.
 *
 * 시스템이 보고하는 xdpi/ydpi는 실제 패널과 몇 % 다를 수 있다(실기기에서 외부 5.5", 428ppi로 보고됨).
 * 그래서 PPI는 `해상도 대각선 픽셀 ÷ 실제 대각선(인치)`으로 계산한다. 실제 대각선의 우선순위는
 * 사용자 입력 → 알려진 기종의 제조사 사양(해상도로 식별) → 시스템 보고값이다.
 */
object PanelSpec {

    data class Spec(val diagonalInch: Float, val label: String)

    /** 해상도(짧은변x긴변) → 제조사 공개 사양. Samsung 표기: 메인 = 내부, 커버 = 외부. */
    val known: Map<String, Spec> = mapOf(
        "1768x2208" to Spec(7.6f, "Galaxy Z Fold3 메인"),
        "832x2268" to Spec(6.2f, "Galaxy Z Fold3 커버"),
        "1812x2176" to Spec(7.6f, "Galaxy Z Fold4·Fold5 메인"),
        "904x2316" to Spec(6.2f, "Galaxy Z Fold4·Fold5 커버"),
        "1856x2160" to Spec(7.6f, "Galaxy Z Fold6 메인"),
        "968x2376" to Spec(6.3f, "Galaxy Z Fold6 커버"),
        "1968x2184" to Spec(8.0f, "Galaxy Z Fold7 메인"),
        "1080x2520" to Spec(6.5f, "Galaxy Z Fold7 커버"),
    )

    enum class Source(val label: String) {
        SPEC("제조사 사양"),
        INACTIVE("비활성 디스플레이 보고값"),
        REPORTED("시스템 보고값"),
    }

    const val MIN_DIAGONAL = 3f
    const val MAX_DIAGONAL = 15f

    data class Resolved(
        /** 보정 계산에 쓰는 값(ppi·대각선이 물리 기준으로 바뀐 ScreenInfo). */
        val screen: ScreenInfo,
        /** 시스템이 보고한 원래 값. */
        val reported: ScreenInfo,
        val source: Source,
        val specLabel: String?,
    )

    fun pixels(key: String): Pair<Int, Int>? {
        val p = key.split('x')
        if (p.size != 2) return null
        val w = p[0].toIntOrNull() ?: return null
        val h = p[1].toIntOrNull() ?: return null
        return if (w > 0 && h > 0) w to h else null
    }

    fun ppi(key: String, diagonalInch: Float): Float? {
        val (w, h) = pixels(key) ?: return null
        if (diagonalInch !in MIN_DIAGONAL..MAX_DIAGONAL) return null
        return (hypot(w.toDouble(), h.toDouble()) / diagonalInch).toFloat()
    }

    /**
     * PPI 결정 순서: 알려진 기종의 제조사 사양 → (켜진 디스플레이 보고값이 두 화면에서 똑같아 믿을 수 없을 때)
     * 꺼져 있을 때 보고한 값 → 켜져 있을 때 보고한 값.
     */
    fun resolve(reported: ScreenInfo, inactivePpi: Float? = null, all: List<ScreenInfo> = listOf(reported)): Resolved {
        known[reported.key]?.let { spec ->
            ppi(reported.key, spec.diagonalInch)?.let {
                return Resolved(reported.copy(ppi = it, diagonalInch = spec.diagonalInch), reported, Source.SPEC, spec.label)
            }
        }
        if (inactivePpi != null && inactivePpi in ScreenPolicy.MIN_PPI..ScreenPolicy.MAX_PPI && activeReadingsIdentical(all)) {
            val diag = pixels(reported.key)?.let { (w, h) -> (hypot(w.toDouble(), h.toDouble()) / inactivePpi).toFloat() }
            if (diag != null) return Resolved(reported.copy(ppi = inactivePpi, diagonalInch = diag), reported, Source.INACTIVE, null)
        }
        return Resolved(reported, reported, Source.REPORTED, null)
    }

    /** 서로 다른 화면이 켜져 있을 때 똑같은 PPI를 보고했다면 그 값은 패널 고유 값이 아니다. */
    fun activeReadingsIdentical(all: List<ScreenInfo>): Boolean {
        val known = all.filter(ScreenPolicy::isPlausible).distinctBy { it.key }
        if (known.size < 2) return false
        val first = known.first().ppi
        return known.all { kotlin.math.abs(it.ppi - first) < 0.5f }
    }
}

/**
 * 목표 DPI 계산에 필요한 값 묶음. 부팅·감시·화면 표시가 모두 같은 계산을 쓰도록 한곳에 모은다.
 */
data class DensityPlan(
    val referenceDpi: Int,
    val reference: ScreenPolicy.Role,
    /** 보정 대상(기준이 아닌) 화면에 더할 미세조정 값(dpi). */
    val adjust: Int,
    val panels: List<PanelSpec.Resolved>,
) {
    val screens: List<ScreenInfo> get() = panels.map { it.screen }

    /** [seen]과 해상도가 같은 학습된 화면의 물리 값으로 목표를 계산한다. 보고 PPI는 쓰지 않는다. */
    fun targetFor(seen: ScreenInfo?): ScreenPolicy.Target {
        val active = seen?.let { s -> screens.find { it.key == s.key } ?: s }
        return ScreenPolicy.effectiveTarget(referenceDpi, reference, screens, active, adjust)
    }

    data class Row(val role: ScreenPolicy.Role, val panel: PanelSpec.Resolved, val dpi: Int, val blocked: String?)

    /** 외부·내부 각각의 목표. 두 화면을 모두 알아야 계산된다. */
    fun rows(): List<Row> = ScreenPolicy.Role.entries.mapNotNull { role ->
        val s = ScreenPolicy.screenFor(screens, role) ?: return@mapNotNull null
        val panel = panels.first { it.screen.key == s.key }
        val t = targetFor(s)
        Row(role, panel, t.dpi, t.blocked)
    }
}

fun SettingsRepository.resolvedPanels(): List<PanelSpec.Resolved> {
    val all = screens
    val inactive = inactivePpi
    return all.map { PanelSpec.resolve(it, inactive[it.key], all) }
}

fun SettingsRepository.plan(): DensityPlan? {
    val base = targetDpi ?: return null
    return DensityPlan(referenceDpi = base, reference = reference, adjust = adjust, panels = resolvedPanels())
}

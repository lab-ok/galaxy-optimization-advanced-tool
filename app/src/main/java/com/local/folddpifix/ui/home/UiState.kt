package com.local.folddpifix.ui.home

import com.local.folddpifix.domain.ScreenGeometry
import com.local.folddpifix.data.display.DisplayDensity
import com.local.folddpifix.data.display.DpiState
import com.local.folddpifix.data.settings.LastApply
import com.local.folddpifix.domain.DensityPlan
import com.local.folddpifix.domain.ExternalChange
import com.local.folddpifix.domain.ScreenInfo
import com.local.folddpifix.domain.ScreenPolicy

data class UiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val hasPermission: Boolean = false,
    val dpi: DpiState? = null,
    /** 기준 디스플레이에 쓸 밀도(사용자 입력). */
    val target: Int? = null,
    val reference: ScreenPolicy.Role = ScreenPolicy.Role.OUTER,
    val adjust: Int = 0,
    val plan: DensityPlan? = null,
    val activeScreen: ScreenInfo? = null,
    /** 지금 켜진 디스플레이에 적용할 밀도(보정 반영). */
    val effective: ScreenPolicy.Target? = null,
    val autoRecover: Boolean = true,
    val applyOnBoot: Boolean = true,
    val watchFold: Boolean = true,
    val lastApply: LastApply? = null,
    val displays: List<DisplayDensity> = emptyList(),
    val hasCrash: Boolean = false,
    val inactivePpi: Map<String, Float> = emptyMap(),
    /** 사용자 선택을 기다리는 외부 DPI 변경. */
    val external: ExternalChange? = null,
    /** 크기 테스트 자를 카드로 맞춘 PPI(화면 key별). */
    val rulerPpi: Map<String, Float> = emptyMap(),
) {
    val status: Status
        get() = when {
            effective == null -> Status.NO_TARGET
            effective.blocked != null -> Status.BLOCKED
            dpi?.current == effective.dpi -> Status.OK
            else -> Status.NEEDS_FIX
        }

    enum class Status { OK, NEEDS_FIX, NO_TARGET, BLOCKED }

    val rows: List<DensityPlan.Row> get() = plan?.rows().orEmpty()

    /** 자동 적용 스위치 하나의 상태. */
    val auto: Boolean get() = autoRecover && applyOnBoot

    /** 학습한 화면의 가로÷세로 비(그림용). 모르면 null. */
    fun aspectOf(role: ScreenPolicy.Role): Float? {
        val screens = plan?.screens ?: return null
        val s = ScreenPolicy.screenFor(screens, role) ?: return null
        val cover = ScreenPolicy.screenFor(screens, ScreenPolicy.Role.OUTER)?.key
        val (w, h) = ScreenGeometry.orientedSize(s.key, cover, role == ScreenPolicy.Role.INNER) ?: return null
        return w / h
    }

    fun roleOf(key: String): ScreenPolicy.Role? = plan?.let { ScreenPolicy.roleOf(it.screens, key) }
}

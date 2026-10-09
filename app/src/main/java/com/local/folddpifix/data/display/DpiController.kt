package com.local.folddpifix.data.display

import com.local.folddpifix.domain.ScreenInfo
/** `wm density` 출력과 같은 의미의 현재 상태. */
data class DpiState(
    /** 기기 기본 density (wm density의 Physical density). */
    val physical: Int?,
    /** 강제 적용된 density. 없으면 null (wm density의 Override density). */
    val override: Int?,
    /** 상태를 읽은 경로. */
    val source: String,
) {
    /** 현재 실제로 쓰이는 density. */
    val current: Int? get() = override ?: physical
}

/** 디스플레이 번호(`wm density -d <id>`)별 상태. */
data class DisplayDensity(
    val id: Int,
    /** 해상도·PPI를 읽을 수 있으면 채운다. 꺼져 있는 디스플레이는 못 읽을 수 있다. */
    val screen: ScreenInfo?,
    val physical: Int,
    /** 강제 적용값. 없거나 읽을 수 없으면 null. */
    val override: Int?,
    /** 현재 값을 읽을 수 있었는지(꺼진 디스플레이는 못 읽을 수 있다). */
    val readable: Boolean,
) {
    val current: Int? get() = if (readable) override ?: physical else null
}

data class ApplyResult(
    val success: Boolean,
    val method: String,
    val detail: String,
)

/** DPI를 읽고 바꾸는 기능. 실제 구현은 [DpiManager]이고 테스트에서는 가짜 구현을 쓴다. */
interface DpiController {
    fun hasPermission(): Boolean
    fun read(): DpiState
    fun apply(dpi: Int): ApplyResult
    fun reset(): ApplyResult
    /** 지금 켜져 있는 물리 화면. 읽지 못하면 null. */
    fun currentScreen(): ScreenInfo?
    /** 기본 display(0) 외의 디스플레이까지 번호별 상태. */
    fun displays(): List<DisplayDensity> = emptyList()
    /** 특정 디스플레이 번호에 적용(`wm density <dpi> -d <id>`와 같음). */
    fun applyTo(displayId: Int, dpi: Int): ApplyResult = ApplyResult(false, "-", "지원하지 않음")
}

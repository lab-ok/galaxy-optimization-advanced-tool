package com.local.folddpifix.domain

/**
 * 화면 크기 계산(순수 함수). 화면 방향, 한 화면에 들어가는 줄 수, dp의 실제 길이.
 * UI는 이 결과를 그리기만 한다.
 */
object ScreenGeometry {

    /** 외부 화면 세로(긴 변)를 모를 때 쓰는 값(SM-F971N). */
    private const val DEFAULT_COVER_HEIGHT = 1972f

    /**
     * 학습한 해상도(짧은변x긴변)를 실제 방향의 (가로, 세로)로 돌려준다.
     * 외부 화면은 세로로 긴 방향이다. 내부 화면은 두 방향 중 세로 길이가 외부 화면의 세로(긴 변)와
     * 더 가까운 쪽을 고른다. 폴드는 접어도 펴도 기기 높이가 같기 때문이다.
     * Fold8(1848x2448, 외부 1972)은 가로로 길고, Fold7(1968x2184, 외부 2520)은 세로로 조금 길다.
     */
    fun orientedSize(key: String, coverKey: String?, isInner: Boolean): Pair<Float, Float>? {
        val (s, l) = PanelSpec.pixels(key)?.let { it.first.toFloat() to it.second.toFloat() } ?: return null
        if (!isInner) return s to l
        val coverH = coverKey?.let(PanelSpec::pixels)?.second?.toFloat() ?: DEFAULT_COVER_HEIGHT
        return if (kotlin.math.abs(s - coverH) < kotlin.math.abs(l - coverH)) l to s else s to l
    }

    /** 줄 세기에 쓰는 한 줄 높이(dp). 목록 한 줄(48dp)과 같다. */
    const val ROW_DP = 48

    /** 세로 [heightPx] 화면에 [dpi]로 [ROW_DP] 줄이 몇 줄 들어가는지. */
    fun rowsFor(heightPx: Float, dpi: Int): Float = heightPx / (ROW_DP * dpi / 160f)

    /** dp 길이가 [dpi]·[ppi] 화면에서 차지하는 실제 길이(mm). */
    fun dpToMm(dp: Int, dpi: Int, ppi: Float): Float = dp * dpi / 160f / ppi * 25.4f

    /** 신용카드·신분증(ISO/IEC 7810 ID-1)의 짧은 변 길이(mm). 자를 맞추는 기준으로 쓴다. */
    const val CARD_SHORT_MM = 53.98f

    /** 기준 PPI로 그린 [mm] 길이를 사용자가 [scale]배로 늘려 실물과 맞췄을 때의 실제 PPI. */
    fun calibratedPpi(basePpi: Float, scale: Float): Float = basePpi * scale

    /** [ppi] 화면에서 [mm] 길이의 픽셀 수. */
    fun mmToPx(mm: Float, ppi: Float): Float = mm * ppi / 25.4f
}

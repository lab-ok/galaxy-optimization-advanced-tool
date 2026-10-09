package com.local.folddpifix.ui.art

/**
 * 그림에 쓰는 기기 비율. 사용자 기기 SM-F971N(Galaxy Z Fold8)의 실제 화면 해상도에서 가져왔다.
 * - 외부(커버) 1248 × 1972: 세로로 긴 화면
 * - 내부(메인) 2448 × 1848: 커버 옆에 화면을 하나 더 붙인 꼴이라 가로로 긴 화면
 * 기기 정보를 배운 뒤에는 학습한 해상도로 그림 비율을 바꾼다.
 */
object FoldGeometry {
    const val COVER_W = 1248f
    const val COVER_H = 1972f
    const val INNER_W = 2448f
    const val INNER_H = 1848f

    /** 가로 ÷ 세로 */
    const val COVER_ASPECT = COVER_W / COVER_H
    const val INNER_ASPECT = INNER_W / INNER_H

    /** 화면 모서리 곡률(높이 대비). 폴드 화면은 모서리가 아주 살짝만 둥글다. */
    const val SCREEN_CORNER = 0.025f
}

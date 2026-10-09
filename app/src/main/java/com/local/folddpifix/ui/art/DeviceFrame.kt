package com.local.folddpifix.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.local.folddpifix.ui.liquid.LiquidColors

/**
 * 앱 그림의 공통 기기 틀(사용자가 고른 FoldDeviceArt 스타일). 모든 기기·화면 그림은 이 함수로 그린다.
 * 잉크색 몸체 → 밝은 화면 → 화면 안쪽의 옅은 회색 테두리 순서이고, 곡률은 폴드 화면처럼 아주 작다.
 * 베젤·곡률은 틀의 높이 기준이다. 폴드는 접어도 펴도 높이가 같으므로 외부·내부 화면 그림의 베젤이 같은 두께로 보인다.
 *
 * @param emphasis 0~1. 1이면 몸체가 진한 잉크, 0이면 옅게(켜지지 않은 화면).
 * @param glow 0~1. 안쪽 회색 테두리가 굵고 진해진다(사용 중인 화면의 숨쉬기).
 * @return 화면(안쪽) 영역
 */
internal fun DrawScope.deviceFrame(
    c: LiquidColors,
    topLeft: Offset,
    size: Size,
    emphasis: Float = 1f,
    glow: Float = 0f,
): Rect {
    val base = size.height
    val bezel = maxOf(base * BEZEL, 2.5.dp.toPx())
    drawRoundRect(c.ink.copy(alpha = 0.35f + 0.65f * emphasis), topLeft, size, CornerRadius(base * BODY_CORNER))
    val screen = Rect(topLeft.x + bezel, topLeft.y + bezel, topLeft.x + size.width - bezel, topLeft.y + size.height - bezel)
    val r = CornerRadius(base * FoldGeometry.SCREEN_CORNER)
    drawRoundRect(c.surface, screen.topLeft, screen.size, r)
    drawRoundRect(
        c.ink.copy(alpha = 0.10f + 0.10f * glow), screen.topLeft, screen.size, r,
        style = Stroke(2.dp.toPx() + 2.dp.toPx() * glow),
    )
    return screen
}

/** 모니터 틀: [deviceFrame] 아래에 같은 잉크색 받침대. 화면 영역을 돌려준다. */
internal fun DrawScope.monitorFrame(c: LiquidColors, center: Offset, width: Float): Rect {
    val h = width * 0.62f
    val tl = Offset(center.x - width / 2, center.y - h / 2 - width * 0.06f)
    val screen = deviceFrame(c, tl, Size(width, h))
    val stroke = maxOf(3.dp.toPx(), width * 0.035f)
    drawLine(c.ink, Offset(center.x, tl.y + h), Offset(center.x, tl.y + h + width * 0.08f), stroke)
    drawLine(c.ink, Offset(center.x - width * 0.18f, tl.y + h + width * 0.08f), Offset(center.x + width * 0.18f, tl.y + h + width * 0.08f), stroke, StrokeCap.Round)
    return screen
}

/** 높이 대비 베젤 두께와 몸체 곡률. */
private const val BEZEL = 0.035f
private const val BODY_CORNER = 0.045f

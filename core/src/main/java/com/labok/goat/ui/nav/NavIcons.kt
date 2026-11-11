package com.labok.goat.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.Space
import com.labok.goat.ui.liquid.LiquidTextField
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.liquidPress
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.rememberSoftBlob
import com.labok.goat.ui.text.Copy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 사이드바·카드·화면이 함께 쓰는 기능 아이콘(공통 모듈, 11-11 모듈 분리 때 SideDrawer.kt에서 옮김). */
enum class NavIcon { HOME, FOLD, APPS, SPEED, ROTATE, CAMERA, CLEAN, REFRESH, GAME, MOON, BELL, PIXELS, FLASK, AIRPLANE, EYE_OFF, FLEX, NOTE, STATS, VOLUME, INFO, GEAR, GLOW }

/** 메뉴 아이콘(24dp). 앱 그림과 같은 둥근 선으로 그린다. */
@Composable
fun NavGlyph(icon: NavIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val s = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            NavIcon.HOME -> home(color, s)
            NavIcon.SPEED -> speed(color, s)
            NavIcon.CLEAN -> clean(color, s)
            NavIcon.REFRESH -> refresh(color, s)
            NavIcon.GAME -> game(color, s)
            NavIcon.MOON -> moon(color, s)
            NavIcon.BELL -> bell(color, s)
            NavIcon.PIXELS -> pixels(color, s)
            NavIcon.FOLD -> fold(color, s)
            NavIcon.APPS -> apps(color, s)
            NavIcon.ROTATE -> rotate(color, s)
            NavIcon.CAMERA -> camera(color, s)
            NavIcon.FLASK -> flask(color, s)
            NavIcon.AIRPLANE -> airplane(color, s)
            NavIcon.EYE_OFF -> eyeOff(color, s)
            NavIcon.FLEX -> flex(color, s)
            NavIcon.STATS -> stats(color, s)
            NavIcon.NOTE -> note(color, s)
            NavIcon.VOLUME -> volume(color, s)
            NavIcon.INFO -> info(color, s)
            NavIcon.GEAR -> gear(color, s)
            NavIcon.GLOW -> glow(color, s)
        }
    }
}

private fun DrawScope.u(v: Float) = v * size.minDimension / 24f
private fun DrawScope.p(x: Float, y: Float) = Offset(u(x), u(y))

/*
 * 메뉴 아이콘: 24 격자, 선 굵기 1.8dp, 둥근 끝·둥근 이음. 바깥 여백 2~2.5를 지켜 다섯 아이콘의 크기감을 맞춘다.
 */

/** 고급 화면 배율 설정: 펼친 폴드(가운데 접는 선) 양쪽 화면에 같은 길이의 글줄 두 개(두 화면 글자 크기가 같다). */
private fun DrawScope.fold(c: Color, s: Stroke) {
    drawRoundRect(c, p(2.5f, 5f), Size(u(19f), u(14f)), CornerRadius(u(2.4f)), style = s)
    drawLine(c, p(12f, 5f), p(12f, 19f), s.width * 0.75f, StrokeCap.Round)
    for (x in listOf(5.5f, 15f)) {
        drawLine(c, p(x, 10f), p(x + 3.5f, 10f), s.width, StrokeCap.Round)
        drawLine(c, p(x, 14f), p(x + 2.2f, 14f), s.width, StrokeCap.Round)
    }
}

/** 앱별 화면 배율: 앱 네 개 중 오른쪽 아래 하나가 커지는 중(바깥으로 뻗는 화살표). */
private fun DrawScope.apps(c: Color, s: Stroke) {
    val r = CornerRadius(u(2f))
    drawRoundRect(c, p(3f, 3f), Size(u(7f), u(7f)), r, style = s)
    drawRoundRect(c, p(14f, 3f), Size(u(7f), u(7f)), r, style = s)
    drawRoundRect(c, p(3f, 14f), Size(u(7f), u(7f)), r, style = s)
    drawRoundRect(c, p(14f, 14f), Size(u(7f), u(7f)), r, style = s)
    // 커지는 앱: 안쪽 대각선 화살표
    drawLine(c, p(16.2f, 18.8f), p(18.8f, 16.2f), s.width * 0.85f, StrokeCap.Round)
    drawLine(c, p(18.8f, 16.2f), p(16.6f, 16.2f), s.width * 0.85f, StrokeCap.Round)
    drawLine(c, p(18.8f, 16.2f), p(18.8f, 18.4f), s.width * 0.85f, StrokeCap.Round)
}

/**
 * 흔들어서 화면 회전: 비스듬히 든 폰과 그 둘레를 도는 두 호(화살촉은 호 끝의 진행 방향으로), 양옆에 흔들림 표시.
 */
private fun DrawScope.rotate(c: Color, s: Stroke) {
    val center = p(12f, 12f)
    // 폰: 7×12, -30도 기울임, 위쪽에 스피커 점
    withTransform({ rotate(-30f, center) }) {
        drawRoundRect(c, p(8.5f, 6f), Size(u(7f), u(12f)), CornerRadius(u(1.8f)), style = s)
        drawLine(c, p(11f, 8f), p(13f, 8f), s.width * 0.8f, StrokeCap.Round)
    }
    // 두 호: 반지름 9.5, 오른쪽 위와 왼쪽 아래(시계 방향으로 돈다)
    val rad = u(9.5f)
    fun arcWithHead(start: Float, sweep: Float) {
        drawArc(c, start, sweep, false, Offset(center.x - rad, center.y - rad), Size(rad * 2, rad * 2), style = s)
        val e = Math.toRadians((start + sweep).toDouble())
        val tip = Offset(center.x + rad * cos(e).toFloat(), center.y + rad * sin(e).toFloat())
        // 시계 방향 진행 방향 = (-sin, cos). 화살촉은 진행 방향 뒤로 ±40도
        val dir = Math.atan2(cos(e), -sin(e))
        val len = u(2.8f)
        for (a in listOf(dir + Math.PI - 0.7, dir + Math.PI + 0.7)) {
            drawLine(c, tip, Offset(tip.x + len * cos(a).toFloat(), tip.y + len * sin(a).toFloat()), s.width, StrokeCap.Round)
        }
    }
    arcWithHead(-75f, 55f)
    arcWithHead(105f, 55f)
}

/** 카메라 촬영음 끄기: 카메라와 오른쪽 위로 그은 사선(소리 끔). */
private fun DrawScope.camera(c: Color, s: Stroke) {
    val body = Path().apply {
        moveTo(u(4.5f), u(8f))
        lineTo(u(8f), u(8f))
        lineTo(u(9.5f), u(5.5f))
        lineTo(u(14.5f), u(5.5f))
        lineTo(u(16f), u(8f))
        lineTo(u(19.5f), u(8f))
        quadraticTo(u(21f), u(8f), u(21f), u(9.5f))
        lineTo(u(21f), u(17.5f))
        quadraticTo(u(21f), u(19f), u(19.5f), u(19f))
        lineTo(u(4.5f), u(19f))
        quadraticTo(u(3f), u(19f), u(3f), u(17.5f))
        lineTo(u(3f), u(9.5f))
        quadraticTo(u(3f), u(8f), u(4.5f), u(8f))
        close()
    }
    drawPath(body, c, style = s)
    drawCircle(c, u(3.4f), p(12f, 13.2f), style = s)
    drawLine(c, p(3f, 21.5f), p(21f, 3.5f), s.width, StrokeCap.Round)
}

/** 홈: 지붕과 문이 있는 집. */
private fun DrawScope.home(c: Color, s: Stroke) {
    val path = Path().apply {
        moveTo(u(3.5f), u(11f))
        lineTo(u(12f), u(4f))
        lineTo(u(20.5f), u(11f))
        moveTo(u(5.5f), u(9.5f))
        lineTo(u(5.5f), u(18.5f))
        quadraticTo(u(5.5f), u(20f), u(7f), u(20f))
        lineTo(u(17f), u(20f))
        quadraticTo(u(18.5f), u(20f), u(18.5f), u(18.5f))
        lineTo(u(18.5f), u(9.5f))
    }
    drawPath(path, c, style = s)
    drawLine(c, p(10f, 20f), p(10f, 15f), s.width, StrokeCap.Round)
    drawLine(c, p(10f, 15f), p(14f, 15f), s.width, StrokeCap.Round)
    drawLine(c, p(14f, 15f), p(14f, 20f), s.width, StrokeCap.Round)
}

/** 애니메이션 속도: 계기판 반원과 오른쪽으로 기운 바늘. */
private fun DrawScope.speed(c: Color, s: Stroke) {
    drawArc(c, 180f, 180f, false, p(3f, 6f), Size(u(18f), u(18f)), style = s)
    drawLine(c, p(12f, 15f), p(16.8f, 9.6f), s.width, StrokeCap.Round)
    drawCircle(c, s.width * 0.9f, p(12f, 15f))
    drawLine(c, p(3f, 19f), p(21f, 19f), s.width * 0.8f, StrokeCap.Round)
}

/** 기본 앱 정리: 앱 칸 하나와 그 위에 겹친 빼기 동그라미. */
private fun DrawScope.clean(c: Color, s: Stroke) {
    drawRoundRect(c, p(3.5f, 3.5f), Size(u(11f), u(11f)), CornerRadius(u(2.6f)), style = s)
    drawCircle(c, u(5.2f), p(16f, 16f), style = s)
    drawLine(c, p(13.6f, 16f), p(18.4f, 16f), s.width, StrokeCap.Round)
}

/** 주사율 고정: 화면 틀 안에서 오른쪽으로 달리는 공과 그 뒤의 촘촘한 잔상 세 줄(빠르게 새로 그림). */
private fun DrawScope.refresh(c: Color, s: Stroke) {
    drawRoundRect(c, p(2.5f, 4.5f), Size(u(19f), u(15f)), CornerRadius(u(2.4f)), style = s)
    drawCircle(c, u(2.4f), p(15.5f, 12f))
    for ((i, x) in listOf(7f, 9.6f, 12.2f).withIndex()) {
        val h = 1.6f + i * 0.9f
        drawLine(c.copy(alpha = 0.45f + i * 0.2f), p(x, 12f - h), p(x, 12f + h), s.width * 0.8f, StrokeCap.Round)
    }
}

/** GOS 끄기: 게임 패드(왼쪽 십자 키, 오른쪽 버튼 둘). */
private fun DrawScope.game(c: Color, s: Stroke) {
    val pad = Path().apply {
        moveTo(u(7f), u(7f))
        lineTo(u(17f), u(7f))
        cubicTo(u(20.5f), u(7f), u(22f), u(10f), u(22f), u(14f))
        cubicTo(u(22f), u(17.5f), u(20f), u(19f), u(18.4f), u(19f))
        cubicTo(u(16.6f), u(19f), u(15.6f), u(16.5f), u(14.5f), u(16.5f))
        lineTo(u(9.5f), u(16.5f))
        cubicTo(u(8.4f), u(16.5f), u(7.4f), u(19f), u(5.6f), u(19f))
        cubicTo(u(4f), u(19f), u(2f), u(17.5f), u(2f), u(14f))
        cubicTo(u(2f), u(10f), u(3.5f), u(7f), u(7f), u(7f))
        close()
    }
    drawPath(pad, c, style = s)
    drawLine(c, p(5.4f, 12f), p(9.4f, 12f), s.width, StrokeCap.Round)
    drawLine(c, p(7.4f, 10f), p(7.4f, 14f), s.width, StrokeCap.Round)
    drawCircle(c, u(1.1f), p(16f, 12.8f))
    drawCircle(c, u(1.1f), p(18.6f, 10.6f))
}

/** 잠자기 모드 끄기: 알림 종과 오른쪽 위의 작은 z(잠). */
private fun DrawScope.bell(c: Color, s: Stroke) {
    val body = Path().apply {
        moveTo(u(4.5f), u(17f))
        cubicTo(u(6f), u(15.5f), u(6f), u(13.5f), u(6f), u(11f))
        cubicTo(u(6f), u(7.4f), u(8.2f), u(5f), u(11.5f), u(5f))
        cubicTo(u(14.8f), u(5f), u(17f), u(7.4f), u(17f), u(11f))
        cubicTo(u(17f), u(13.5f), u(17f), u(15.5f), u(18.5f), u(17f))
        close()
    }
    drawPath(body, c, style = s)
    drawLine(c, p(9.8f, 20f), p(13.2f, 20f), s.width, StrokeCap.Round)
    // z
    val z = Path().apply {
        moveTo(u(17.6f), u(2.6f))
        lineTo(u(21.4f), u(2.6f))
        lineTo(u(17.6f), u(6.6f))
        lineTo(u(21.4f), u(6.6f))
    }
    drawPath(z, c, style = Stroke(s.width * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 앱 다크 모드 강제: 초승달과 그 옆 작은 별 둘. */
private fun DrawScope.moon(c: Color, s: Stroke) {
    val crescent = Path().apply {
        moveTo(u(14.5f), u(3.6f))
        cubicTo(u(8.2f), u(3.4f), u(4.2f), u(8.6f), u(5.4f), u(14f))
        cubicTo(u(6.6f), u(19.4f), u(13.4f), u(22f), u(18.4f), u(18.2f))
        cubicTo(u(12.4f), u(18.4f), u(9.4f), u(10.6f), u(14.5f), u(3.6f))
        close()
    }
    drawPath(crescent, c, style = s)
    drawCircle(c, u(1.1f), p(18.6f, 7f))
    drawCircle(c, u(0.8f), p(20.4f, 11.6f))
}

/** 앰비언트 모드: 텐트(ㅅ)로 세운 폴드와 그 아래로 퍼지는 빛 세 줄기. */
private fun DrawScope.glow(c: Color, s: Stroke) {
    val tent = Path().apply {
        moveTo(u(5f), u(15.5f))
        lineTo(u(12f), u(4f))
        lineTo(u(19f), u(15.5f))
    }
    drawPath(tent, c, style = s)
    drawLine(c, p(3f, 19.5f), p(21f, 19.5f), s.width, StrokeCap.Round)
    drawLine(c, p(9.5f, 12.5f), p(8.5f, 16.5f), s.width, StrokeCap.Round)
    drawLine(c, p(12f, 12f), p(12f, 16.5f), s.width, StrokeCap.Round)
    drawLine(c, p(14.5f, 12.5f), p(15.5f, 16.5f), s.width, StrokeCap.Round)
}

/** 해상도 낮추기: 화면 틀 안의 굵은 픽셀 네 칸(점이 줄어 굵어짐)과 옆의 작은 픽셀 두 칸. */
private fun DrawScope.pixels(c: Color, s: Stroke) {
    drawRoundRect(c, p(3f, 3f), Size(u(18f), u(18f)), CornerRadius(u(2.6f)), style = s)
    val r = CornerRadius(u(0.8f))
    drawRoundRect(c, p(6.5f, 6.5f), Size(u(4.5f), u(4.5f)), r)
    drawRoundRect(c, p(6.5f, 12.5f), Size(u(4.5f), u(4.5f)), r)
    drawRoundRect(c, p(12.5f, 12.5f), Size(u(4.5f), u(4.5f)), r)
    drawRoundRect(c.copy(alpha = 0.5f), p(13f, 7f), Size(u(1.8f), u(1.8f)), r)
    drawRoundRect(c.copy(alpha = 0.5f), p(15.4f, 9.4f), Size(u(1.8f), u(1.8f)), r)
}

/** 정보: 원 안의 i. */
private fun DrawScope.info(c: Color, s: Stroke) {
    drawCircle(c, u(9f), p(12f, 12f), style = s)
    drawLine(c, p(12f, 11f), p(12f, 16.5f), s.width, StrokeCap.Round)
    drawCircle(c, s.width * 0.75f, p(12f, 7.8f))
}

/** 앱 설정: 톱니바퀴(바깥 이 8개, 가운데 구멍). */
private fun DrawScope.gear(c: Color, s: Stroke) {
    val o = p(12f, 12f)
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        val x = cos(a).toFloat()
        val y = sin(a).toFloat()
        drawLine(c, Offset(o.x + x * u(6.6f), o.y + y * u(6.6f)), Offset(o.x + x * u(9.2f), o.y + y * u(9.2f)), s.width * 1.6f, StrokeCap.Round)
    }
    drawCircle(c, u(6.4f), o, style = s)
    drawCircle(c, u(2.6f), o, style = s)
}

/** 조사 도구: 플라스크, 안에 액체 선과 기포 둘. */
/** 사용 통계: 바닥선 위 높이가 다른 막대 셋. */
private fun DrawScope.stats(c: Color, s: Stroke) {
    drawLine(c, p(4f, 20f), p(20f, 20f), s.width, StrokeCap.Round)
    drawLine(c, p(7.5f, 16.5f), p(7.5f, 12f), s.width * 1.6f, StrokeCap.Round)
    drawLine(c, p(12f, 16.5f), p(12f, 5f), s.width * 1.6f, StrokeCap.Round)
    drawLine(c, p(16.5f, 16.5f), p(16.5f, 9f), s.width * 1.6f, StrokeCap.Round)
}

/** 클립보드 공유: 클립보드 오른쪽 변이 그대로 위로 솟는 화살표가 된다(1.49.1 시안 10번, 타일 아이콘 ic_tile_txt와 같은 그림). */
private fun DrawScope.note(c: Color, s: Stroke) {
    val m = Matrix().apply { scale(u(1f), u(1f)) }
    CLIP_SHARE_PATHS.forEach { drawPath(PathParser().parsePathString(it).toPath().apply { transform(m) }, c, style = s) }
    val thin = s.width * 1.5f / 1.8f
    drawLine(c, p(8.5f, 11f), p(14.5f, 11f), thin, StrokeCap.Round)
    drawLine(c, p(8.5f, 14.2f), p(14.5f, 14.2f), thin, StrokeCap.Round)
    drawLine(c, p(8.5f, 17.4f), p(12f, 17.4f), thin, StrokeCap.Round)
}

/** [note]의 보드·집게·화살촉(24 격자, ic_tile_txt.xml과 같은 경로). */
val CLIP_SHARE_PATHS = listOf(
    "M7.5 4.5h-.5A2 2 0 0 0 5 6.5V19a2.5 2.5 0 0 0 2.5 2.5h9A2.5 2.5 0 0 0 19 19V3",
    "M8.7 2.5h3.6a1.2 1.2 0 0 1 1.2 1.2v1.6a1.2 1.2 0 0 1-1.2 1.2h-3.6a1.2 1.2 0 0 1-1.2-1.2v-1.6a1.2 1.2 0 0 1 1.2-1.2z",
    "M16.4 5.6L19 3l2.6 2.6"
)

/** 스피커와 위아래 화살표(볼륨 버튼 회전). */
private fun DrawScope.volume(c: Color, s: Stroke) {
    val speaker = Path().apply {
        moveTo(u(3.5f), u(9.5f))
        lineTo(u(7f), u(9.5f))
        lineTo(u(11.5f), u(5.5f))
        lineTo(u(11.5f), u(18.5f))
        lineTo(u(7f), u(14.5f))
        lineTo(u(3.5f), u(14.5f))
        close()
    }
    drawPath(speaker, c, style = s)
    // 위 화살표와 아래 화살표: 올리기·내리기가 바뀐다는 뜻
    drawLine(c, p(17.5f, 4.5f), p(17.5f, 10.5f), s.width, StrokeCap.Round)
    drawLine(c, p(15f, 7f), p(17.5f, 4.5f), s.width, StrokeCap.Round)
    drawLine(c, p(20f, 7f), p(17.5f, 4.5f), s.width, StrokeCap.Round)
    drawLine(c, p(17.5f, 13.5f), p(17.5f, 19.5f), s.width, StrokeCap.Round)
    drawLine(c, p(15f, 17f), p(17.5f, 19.5f), s.width, StrokeCap.Round)
    drawLine(c, p(20f, 17f), p(17.5f, 19.5f), s.width, StrokeCap.Round)
}

private fun DrawScope.flask(c: Color, s: Stroke) {
    val path = Path().apply {
        moveTo(u(9.5f), u(3.5f))
        lineTo(u(9.5f), u(9.2f))
        lineTo(u(4.6f), u(17.8f))
        quadraticTo(u(3.9f), u(20.5f), u(6.6f), u(20.5f))
        lineTo(u(17.4f), u(20.5f))
        quadraticTo(u(20.1f), u(20.5f), u(19.4f), u(17.8f))
        lineTo(u(14.5f), u(9.2f))
        lineTo(u(14.5f), u(3.5f))
    }
    drawPath(path, c, style = s)
    drawLine(c, p(8f, 3.5f), p(16f, 3.5f), s.width, StrokeCap.Round)
    drawLine(c, p(6.8f, 14.5f), p(17.2f, 14.5f), s.width * 0.8f, StrokeCap.Round)
    drawCircle(c, u(0.9f), p(10.5f, 17.4f))
    drawCircle(c, u(0.65f), p(13.8f, 16.8f))
}

/** 실험실 비행기 모드 무선: 위에서 본 비행기(동체·주날개·꼬리날개). */
private fun DrawScope.airplane(c: Color, s: Stroke) {
    val path = Path().apply {
        moveTo(u(12f), u(2.8f))
        quadraticTo(u(13.4f), u(2.8f), u(13.4f), u(5f))
        lineTo(u(13.4f), u(9.6f))
        lineTo(u(20.6f), u(13.6f))
        lineTo(u(20.6f), u(15.6f))
        lineTo(u(13.4f), u(13.6f))
        lineTo(u(13.4f), u(17.8f))
        lineTo(u(15.8f), u(19.6f))
        lineTo(u(15.8f), u(21.2f))
        lineTo(u(12f), u(20.2f))
        lineTo(u(8.2f), u(21.2f))
        lineTo(u(8.2f), u(19.6f))
        lineTo(u(10.6f), u(17.8f))
        lineTo(u(10.6f), u(13.6f))
        lineTo(u(3.4f), u(15.6f))
        lineTo(u(3.4f), u(13.6f))
        lineTo(u(10.6f), u(9.6f))
        lineTo(u(10.6f), u(5f))
        quadraticTo(u(10.6f), u(2.8f), u(12f), u(2.8f))
        close()
    }
    drawPath(path, c, style = s)
}

/** 실험실 아이콘 숨기기: 눈(아몬드 모양과 눈동자)에 사선. */
private fun DrawScope.eyeOff(c: Color, s: Stroke) {
    val eye = Path().apply {
        moveTo(u(2.6f), u(12f))
        quadraticTo(u(12f), u(2.4f), u(21.4f), u(12f))
        quadraticTo(u(12f), u(21.6f), u(2.6f), u(12f))
        close()
    }
    drawPath(eye, c, style = s)
    drawCircle(c, u(2.8f), p(12f, 12f), style = s)
    drawLine(c, p(4.2f, 3.8f), p(19.8f, 20.2f), s.width, StrokeCap.Round)
}

/**
 * 자동 플렉스 모드: 노트북처럼 반쯤 접어 세운 폴드를 비스듬히 본 입체 그림. 바닥 화면은 원근으로 납작한 평행사변형,
 * 세운 화면은 뒤로 살짝 기운 사각형이고 두 면이 아래 힌지에서 만난다. 세운 화면 안에 재생 삼각형, 바닥 화면에 조작 점 둘.
 */
private fun DrawScope.flex(c: Color, s: Stroke) {
    // 비스듬히 본 반쯤 접힌 폰: 세운 위 화면과 아래 화면이 힌지에서 만난다.
    val top = Path().apply {
        moveTo(u(8f), u(13.5f))
        lineTo(u(20.3f), u(16.7f))
        lineTo(u(21.3f), u(7.7f))
        lineTo(u(9f), u(4.5f))
        close()
    }
    drawPath(top, c, style = s)
    val base = Path().apply {
        moveTo(u(8f), u(13.5f))
        lineTo(u(20.3f), u(16.7f))
        lineTo(u(15f), u(19.6f))
        lineTo(u(2.7f), u(16.3f))
        close()
    }
    drawPath(base, c, style = s)
    // 힌지 선을 조금 굵게: 접힌 곳
    drawLine(c, p(8f, 13.5f), p(20.3f, 16.7f), s.width * 1.4f, StrokeCap.Round)
    // 아래 화면의 터치패드
    val pad = Path().apply {
        moveTo(u(9.5f), u(15.15f))
        lineTo(u(15.65f), u(16.75f))
        lineTo(u(13f), u(18.2f))
        lineTo(u(6.85f), u(16.6f))
        close()
    }
    drawPath(pad, c)
}

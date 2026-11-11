package com.labok.goat.ui.lab

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 초타원(superellipse) 모서리 모양(11-02 소장님 "Ultimate Spatial Ambient Liquid UX": 고정 28dp 라운드가 아니라 크기에 비례한 유기적 모양).
 *
 * 모서리 반지름은 요소의 짧은 변 × [ratio](0.5면 캡슐)이고, 모서리 곡선은 원호 대신 |x|ⁿ + |y|ⁿ = rⁿ(n = [EXPONENT])라서
 * 직선에서 곡선으로 넘어가는 곳이 매끄럽다(원호는 곡률이 갑자기 바뀐다). 같은 [radiusFor]·[EXPONENT]를 리퀴드 글래스 셰이더의
 * 거리 함수에도 넘겨서, 잘라낸 모양·테두리 빛·굴절 띠가 한 윤곽을 따른다.
 */
class SuperellipseShape(private val ratio: Float) : Shape {
    fun radiusFor(size: Size): Float = (size.minDimension * ratio).coerceAtMost(size.minDimension / 2f)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = radiusFor(size)
        val w = size.width
        val h = size.height
        val path = Path()

        // 모서리마다 곡선을 점으로 근사한다(사분원당 STEPS개). 화면 좌표(y 아래)에서 각도가 커지는 쪽이 시계 방향이다
        fun spow(v: Double) = (if (v < 0) -1.0 else 1.0) * kotlin.math.abs(v).pow(2.0 / EXPONENT)
        var first = true
        fun corner(cx: Float, cy: Float, fromDeg: Double) {
            for (k in 0..STEPS) {
                val t = Math.toRadians(fromDeg + 90.0 * k / STEPS)
                val x = cx + (r * spow(cos(t))).toFloat()
                val y = cy + (r * spow(sin(t))).toFloat()
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
            }
        }
        corner(r, r, 180.0) // 왼쪽 위: 왼쪽 변 → 위 변
        corner(w - r, r, 270.0) // 오른쪽 위
        corner(w - r, h - r, 0.0) // 오른쪽 아래
        corner(r, h - r, 90.0) // 왼쪽 아래
        path.close()
        return Outline.Generic(path)
    }

    companion object {
        /** 초타원 지수(2 = 원호, 클수록 각진 쪽). 4 근처가 iOS 연속 모서리와 비슷한 부드러움이다. */
        const val EXPONENT = 4f
        private const val STEPS = 14

        /** 큰 미디어 버튼(뒤로·재생·앞으로): 아주 둥글되 조작 면적은 넓게. */
        val Media = SuperellipseShape(0.47f)

        /** 캡슐(헤더·닫기·잠금·진행·아래 줄): 짧은 변의 절반. */
        val Capsule = SuperellipseShape(0.5f)
    }
}

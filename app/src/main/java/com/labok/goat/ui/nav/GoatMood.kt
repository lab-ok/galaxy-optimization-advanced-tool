package com.labok.goat.ui.nav

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import com.labok.goat.R
import com.labok.goat.data.lab.LabUnlock
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.UiGoat
import com.labok.goat.ui.text.Copy
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** 염소 표정. */
enum class Mood { CALM, SQUINT, CRY, ANGRY }

/**
 * 염소 아이콘의 표정(사이드바 머리·앱 정보·홈 머리). 화면을 열면 편한 표정 가운데 하나로 시작하고, 누를 때마다 당황·울음·화남
 * 그림 가운데 지금과 다른 하나로 한 번 바뀐 뒤 그대로 둔다(저절로 돌아오거나 단계별로 바뀌지 않는다).
 */
class GoatMoodState {
    var mood by mutableStateOf(Mood.CALM)
        private set

    /** 지금 표정 묶음 안에서 고른 그림 번호(묶음 크기로 나눈 나머지를 쓴다). */
    var variant by mutableIntStateOf(Random.nextInt(1000))
        private set
    internal var presses by mutableIntStateOf(0)
        private set
    private val taps = ArrayDeque<Long>()

    /** 쉬지 않고 이어서 누른 횟수. */
    private var streak = 0

    /** 지금까지 쉬지 않고 이어서 누른 횟수(실험실 열기용). */
    val streakNow get() = streak

    /** 눌렀다. 반응 그림을 지금과 다른 한 장으로 바꾸고 true를 돌려준다. */
    fun press(now: Long = System.currentTimeMillis()): Boolean {
        streak = if (taps.isNotEmpty() && now - taps.last() <= STREAK_GAP_MS) streak + 1 else 1
        taps.addLast(now)
        while (taps.isNotEmpty() && now - taps.first() > WINDOW_MS) taps.removeFirst()
        presses++
        // 반응 그림 8장 가운데 지금과 다른 한 장
        val current = REACTIONS.indexOfFirst { it.first == mood && it.second == variant % faceCount(mood) }
        var pick = Random.nextInt(REACTIONS.size)
        if (pick == current) pick = (pick + 1 + Random.nextInt(REACTIONS.size - 1)) % REACTIONS.size
        mood = REACTIONS[pick].first
        variant = REACTIONS[pick].second
        return true
    }

    /**
     * 누르는 힘 배수: 연달아 누를수록 줄인다. 물방울은 누를 때마다 속도를 더하므로, 빠르게 여러 번 누르면 힘이 쌓여
     * 아이콘 모양이 무너지고 염소가 밖으로 밀려난다. 3초 안에 누른 횟수가 늘수록 1 → 0.25까지 줄인다.
     */
    val impulse: Float get() = (1f / (1f + 0.35f * (taps.size - 1).coerceAtLeast(0))).coerceAtLeast(0.25f)

    companion object {
        const val WINDOW_MS = 3000L

        /** 이 시간 안에 다시 누르면 이어서 누른 것으로 본다(ms). */
        const val STREAK_GAP_MS = 1200L

        /** 누르면 고르는 반응 그림(표정 묶음, 묶음 안 번호). */
        internal val REACTIONS: List<Pair<Mood, Int>> = listOf(Mood.SQUINT, Mood.CRY, Mood.ANGRY).flatMap { m -> (0 until faceCount(m)).map { m to it } }
    }
}

@Composable
fun rememberGoatMood(): GoatMoodState {
    val s = remember { GoatMoodState() }
    val context = LocalContext.current
    // 쉬지 않고 열 번 누르면 숨은 실험실을 연다(어느 염소를 눌러도 같다)
    LaunchedEffect(s.presses) {
        if (s.streakNow >= LabUnlock.TAPS && LabUnlock.unlock(context)) Toast.makeText(context, Copy.LAB_OPENED, Toast.LENGTH_SHORT).show()
    }
    return s
}

/**
 * 염소 캐릭터 그림 20장(2026-10-18, docs: ~/claude_workspace/goat/branding/goat_girl_icons_261018).
 * 평소([Mood.CALM])는 편한 표정 가운데 랜덤, 툭툭 치면 당황([Mood.SQUINT])·울음([Mood.CRY])·화남([Mood.ANGRY]) 묶음에서 고른다.
 */
private val CALM_FACES = intArrayOf(
    R.drawable.goat_face_01, R.drawable.goat_face_02, R.drawable.goat_face_10, R.drawable.goat_face_11, R.drawable.goat_face_12,
    R.drawable.goat_face_13, R.drawable.goat_face_15, R.drawable.goat_face_16, R.drawable.goat_face_17, R.drawable.goat_face_18,
    R.drawable.goat_face_19, R.drawable.goat_face_20
)

/** 당황: 놀람(03)·갸우뚱(08)·질끈(14). */
private val STARTLED_FACES = intArrayOf(R.drawable.goat_face_03, R.drawable.goat_face_08, R.drawable.goat_face_14)

/** 울음: 울먹(04)·엉엉(05). */
private val CRY_FACES = intArrayOf(R.drawable.goat_face_04, R.drawable.goat_face_05)

/** 화남: 화남(06)·삐침(07)·어지러움(09). */
private val ANGRY_FACES = intArrayOf(R.drawable.goat_face_06, R.drawable.goat_face_07, R.drawable.goat_face_09)

/**
 * 지금 표정의 염소 그림. 캐릭터 그림([UiGoat.Style.ART])은 정사각형이라 [drawGoat]가 로고 물방울 모양으로 잘라 그리고,
 * 이전 선 그림([UiGoat.Style.CLASSIC])은 표정마다 한 장씩이다.
 */
@Composable
fun goatPainter(mood: Mood, variant: Int): Painter {
    if (UiGoat.style == UiGoat.Style.CLASSIC) {
        val res = when (mood) {
            Mood.CALM -> R.drawable.ic_goat
            Mood.SQUINT -> R.drawable.ic_goat_squint
            Mood.CRY -> R.drawable.ic_goat_cry
            Mood.ANGRY -> R.drawable.ic_goat_angry
        }
        return rememberVectorPainter(ImageVector.vectorResource(res))
    }
    val faces = faces(mood)
    return painterResource(faces[variant % faces.size])
}

/**
 * 염소 아이콘 테두리(시안 4 '물방울 고리', 2026-10-23): 원 모양 로고 물방울의 바깥으로 테마 잉크색 고리를 그린다. 고리의 바깥 가장자리는
 * 두께가 물결처럼 바뀌며 [phase]에 따라 계속 천천히 일렁이고, 안쪽은 물방울 외곽선을 따라가 누르거나 출렁일 때 함께 움직인다.
 * 물방울을 채우기 전에(뒤에) 불러야 안쪽이 가려져 바깥 고리만 보인다.
 */
fun DrawScope.drawGoatRing(clip: Path, color: Color, logoS: Float, phase: Float) {
    val pm = PathMeasure().apply { setPath(clip, true) }
    val len = pm.length
    if (len <= 0f) return
    val n = 120
    val pts = ArrayList<Offset>(n)
    var cx = 0f
    var cy = 0f
    for (i in 0 until n) {
        val p = pm.getPosition(len * i / n)
        cx += p.x
        cy += p.y
        pts += p
    }
    cx /= n
    cy /= n
    val outer = Path()
    for (i in 0 until n) {
        val u = i.toFloat() / n
        val t = pm.getTangent(len * u)
        val p = pts[i]
        // 바깥(중심 반대쪽)을 향하는 법선
        var nx = -t.y
        var ny = t.x
        if (nx * (p.x - cx) + ny * (p.y - cy) < 0f) {
            nx = -nx
            ny = -ny
        }
        // 시안 4의 두께: 기본 약 7%, 세 번·다섯 번 굽이치는 물결을 겹쳐 천천히 돈다
        val w = logoS * (0.07f + 0.022f * sin(u * 6f * PI.toFloat() + phase) + 0.013f * sin(u * 10f * PI.toFloat() + 1f - phase * 1.3f))
        val x = p.x + nx * w
        val y = p.y + ny * w
        if (i == 0) outer.moveTo(x, y) else outer.lineTo(x, y)
    }
    outer.close()
    drawPath(outer, color)
}

/** 고리가 일렁이는 위상(0~2π, 7초에 한 바퀴). 동작 줄이기가 켜져 있으면 멈춘다. */
@Composable
fun goatRingPhase(): Float {
    val reduce = LocalReduceMotion.current
    val t by rememberInfiniteTransition(label = "goatRing").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "phase")
    return if (reduce) 0f else t
}

/**
 * 로고 물방울 안에 염소를 그린다. [clip]은 로고 물방울 모양, (logoX, logoY)는 그 중심, (dropX, dropY)는 안쪽 방울 중심, [logoS]는 로고 크기.
 * 캐릭터 그림은 물방울보다 넉넉히 크게 그리고 안쪽 방울을 절반 이하로만 따라가게 해, 끌거나 출렁여도 가장자리에 빈 곳이 생기지 않는다.
 * 이전 선 그림은 예전처럼 안쪽 방울 중심을 그대로 따라가며 고개를 갸웃한다(배경이 투명해 자르지 않는다).
 */
fun DrawScope.drawGoat(goat: Painter, clip: Path, logoX: Float, logoY: Float, dropX: Float, dropY: Float, logoS: Float) {
    val tilt = ((dropX - logoX) / logoS * 40f).coerceIn(-14f, 14f)
    if (UiGoat.style == UiGoat.Style.CLASSIC) {
        val gs = logoS * 0.98f
        rotate(tilt, Offset(dropX, dropY)) {
            translate(dropX - gs / 2, dropY - gs / 2) { with(goat) { draw(Size(gs, gs)) } }
        }
        return
    }
    val gs = logoS * 1.25f
    clipPath(clip) {
        val gx = logoX + (dropX - logoX) * 0.35f
        val gy = logoY + (dropY - logoY) * 0.35f
        rotate(tilt * 0.5f, Offset(gx, gy)) {
            translate(gx - gs / 2, gy - gs / 2) { with(goat) { draw(Size(gs, gs)) } }
        }
    }
}

private fun faces(mood: Mood) = when (mood) {
    Mood.CALM -> CALM_FACES
    Mood.SQUINT -> STARTLED_FACES
    Mood.CRY -> CRY_FACES
    Mood.ANGRY -> ANGRY_FACES
}

internal fun faceCount(mood: Mood) = faces(mood).size

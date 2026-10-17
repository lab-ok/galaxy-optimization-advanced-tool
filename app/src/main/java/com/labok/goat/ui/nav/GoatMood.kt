package com.labok.goat.ui.nav

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.labok.goat.R
import com.labok.goat.data.lab.LabUnlock
import com.labok.goat.ui.text.Copy
import kotlin.random.Random
import kotlinx.coroutines.delay

/** 염소 표정. */
enum class Mood { CALM, SQUINT, CRY, ANGRY }

/**
 * 염소 아이콘의 표정(사이드바 머리·앱 정보·홈 머리). 누를 때마다 잠깐 찡그리고, 쉬지 않고 이어서 [CRY_AT]번 누르면 울고,
 * 계속 누르면 [CYCLE]번마다 화남(볼이 빨개짐)과 울음이 번갈아 나온다. 손을 떼고 조금 지나면 평소 얼굴로 돌아온다.
 * '이어서'는 앞 누름과 [STREAK_GAP_MS] 안에 다시 누른 것을 말한다.
 */
class GoatMoodState {
    var mood by mutableStateOf(Mood.CALM)
        private set

    /** 지금 표정 묶음 안에서 고른 그림 번호(묶음 크기로 나눈 나머지를 쓴다). 표정이 바뀔 때마다 새로 고른다. */
    var variant by mutableIntStateOf(Random.nextInt(1000))
        private set
    internal var presses by mutableIntStateOf(0)
        private set
    private val taps = ArrayDeque<Long>()

    /** 쉬지 않고 이어서 누른 횟수. */
    private var streak = 0

    /** 지금까지 쉬지 않고 이어서 누른 횟수(실험실 열기용). */
    val streakNow get() = streak

    /** 눌렀다. 표정을 바꾸고, 화가 난 순간이면 true(더 단단한 진동용). 울기 시작한 순간은 [startedCrying]. */
    var startedCrying = false
        private set

    fun press(now: Long = System.currentTimeMillis()): Boolean {
        streak = if (taps.isNotEmpty() && now - taps.last() <= STREAK_GAP_MS) streak + 1 else 1
        taps.addLast(now)
        while (taps.isNotEmpty() && now - taps.first() > WINDOW_MS) taps.removeFirst()
        val before = mood
        mood = moodAt(streak)
        // 표정 묶음이 바뀌면 그 묶음 안에서 그림을 새로 고른다(같은 묶음이면 그대로 둬서 깜빡이지 않게)
        if (mood != before) variant = Random.nextInt(1000)
        presses++
        startedCrying = mood == Mood.CRY && before != Mood.CRY
        return mood == Mood.ANGRY && before != Mood.ANGRY
    }

    internal fun calm() {
        mood = Mood.CALM
        // 평소 얼굴도 돌아올 때마다 랜덤으로
        variant = Random.nextInt(1000)
    }

    /**
     * 누르는 힘 배수: 연달아 누를수록 줄인다. 물방울은 누를 때마다 속도를 더하므로, 빠르게 여러 번 누르면 힘이 쌓여
     * 아이콘 모양이 무너지고 염소가 밖으로 밀려난다. 3초 안에 누른 횟수가 늘수록 1 → 0.25까지 줄인다.
     */
    val impulse: Float get() = (1f / (1f + 0.35f * (taps.size - 1).coerceAtLeast(0))).coerceAtLeast(0.25f)

    companion object {
        const val WINDOW_MS = 3000L
        const val CRY_AT = 5
        const val ANGRY_AT = 10

        /** 울음 다음부터 이만큼 누를 때마다 화남 ↔ 울음이 바뀐다. */
        const val CYCLE = ANGRY_AT - CRY_AT

        /** 이 시간 안에 다시 누르면 이어서 누른 것으로 본다(ms). */
        const val STREAK_GAP_MS = 1200L

        /** 이어서 [n]번째 누름의 표정: 찡그림 → 울음 → 화남 → 울음 → 화남 … */
        fun moodAt(n: Int): Mood = when {
            n < CRY_AT -> Mood.SQUINT
            (n - CRY_AT) / CYCLE % 2 == 0 -> Mood.CRY
            else -> Mood.ANGRY
        }

        /** 표정이 남아 있는 시간(ms). */
        fun holdMs(m: Mood) = when (m) {
            Mood.SQUINT -> 450L
            Mood.CRY -> 1800L
            Mood.ANGRY -> 2400L
            Mood.CALM -> 0L
        }
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
    // 마지막으로 누른 뒤 표정 시간이 지나면 평소 얼굴로
    LaunchedEffect(s.presses) {
        if (s.mood == Mood.CALM) return@LaunchedEffect
        delay(GoatMoodState.holdMs(s.mood))
        s.calm()
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

/** 지금 표정의 염소 그림. 정사각형 그림이라 그리는 쪽에서 로고 물방울 모양으로 잘라 그린다. */
@Composable
fun goatPainter(mood: Mood, variant: Int): Painter {
    val faces = when (mood) {
        Mood.CALM -> CALM_FACES
        Mood.SQUINT -> STARTLED_FACES
        Mood.CRY -> CRY_FACES
        Mood.ANGRY -> ANGRY_FACES
    }
    return painterResource(faces[variant % faces.size])
}

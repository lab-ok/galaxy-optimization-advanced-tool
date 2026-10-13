package com.labok.goat.ui.nav

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.labok.goat.data.lab.LabUnlock
import com.labok.goat.ui.text.Copy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import com.labok.goat.R
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
        presses++
        startedCrying = mood == Mood.CRY && before != Mood.CRY
        return mood == Mood.ANGRY && before != Mood.ANGRY
    }

    internal fun calm() { mood = Mood.CALM }

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
        fun holdMs(m: Mood) = when (m) { Mood.SQUINT -> 450L; Mood.CRY -> 1800L; Mood.ANGRY -> 2400L; Mood.CALM -> 0L }
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

/** 지금 표정의 염소 그림. */
@Composable
fun goatPainter(mood: Mood): VectorPainter {
    val calm = rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_goat))
    val squint = rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_goat_squint))
    val cry = rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_goat_cry))
    val angry = rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_goat_angry))
    return when (mood) { Mood.CALM -> calm; Mood.SQUINT -> squint; Mood.CRY -> cry; Mood.ANGRY -> angry }
}

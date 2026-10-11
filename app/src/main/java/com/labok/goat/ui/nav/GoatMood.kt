package com.labok.goat.ui.nav

import androidx.compose.runtime.Composable
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
 * 염소 아이콘의 표정(사이드바 머리·앱 정보·홈 머리). 누를 때마다 잠깐 찡그리고, 3초 안에 [CRY_AT]번 넘게 누르면 울고,
 * [ANGRY_AT]번 넘게 누르면 화낸다(볼이 빨개짐). 손을 떼고 조금 지나면 평소 얼굴로 돌아온다.
 */
class GoatMoodState {
    var mood by mutableStateOf(Mood.CALM)
        private set
    internal var presses by mutableIntStateOf(0)
        private set
    private val taps = ArrayDeque<Long>()

    /** 눌렀다. 표정을 바꾸고, 화가 난 순간이면 true(더 단단한 진동용). 울기 시작한 순간은 [startedCrying]. */
    var startedCrying = false
        private set

    fun press(now: Long = System.currentTimeMillis()): Boolean {
        taps.addLast(now)
        while (taps.isNotEmpty() && now - taps.first() > WINDOW_MS) taps.removeFirst()
        val before = mood
        mood = when {
            taps.size >= ANGRY_AT -> Mood.ANGRY
            taps.size >= CRY_AT -> Mood.CRY
            else -> Mood.SQUINT
        }
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
        /** 표정이 남아 있는 시간(ms). */
        fun holdMs(m: Mood) = when (m) { Mood.SQUINT -> 450L; Mood.CRY -> 1800L; Mood.ANGRY -> 2400L; Mood.CALM -> 0L }
    }
}

@Composable
fun rememberGoatMood(): GoatMoodState {
    val s = remember { GoatMoodState() }
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

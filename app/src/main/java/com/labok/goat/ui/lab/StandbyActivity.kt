package com.labok.goat.ui.lab

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.labok.goat.R
import com.labok.goat.data.lab.TentDuo
import com.labok.goat.ui.components.Gap
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * 텐트 모드 스탠바이 시계(실험실, 11-02 소장님). 바깥 화면(텐트 모드에서는 기본 화면)에 가로로 꽉 차게 뜬다.
 * 옆으로 넘기면 시계 스타일 11종(리퀴드 + 시안 10종)이 바뀌고 고른 것을 기억한다. 화면을 누르면 스타일 이름·점·[끝내기]가 잠깐 보인다.
 * [EXTRA_CHECK]이면 시계 대신 네 가장자리를 서로 다른 색으로 칠한 방향 확인 화면을 띄운다(안쪽 반사광이 어느 쪽으로 가는지 확인).
 */
class StandbyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val check = intent.getBooleanExtra(EXTRA_CHECK, false)
        val prefs = getSharedPreferences(PREF, Context.MODE_PRIVATE)
        setContent {
            if (check) {
                DirectionCheck(onClose = { TentDuo.stop(this, "방향 확인 끝") })
                return@setContent
            }
            // 반사 시험 화면(GOAT 플렉스 패널 공간 반사 검증용): 1 왼쪽 빨강·오른쪽 파랑(줄무늬), 2 위 흰색·아래 검정, 3 여러 색, 4 전체 검정, 5 전체 흰색
            val pattern = intent.getIntExtra(EXTRA_PATTERN, 0)
            if (pattern > 0) {
                ReflectionPattern(pattern)
                return@setContent
            }
            val now by produceState(LocalDateTime.now()) {
                while (true) {
                    value = LocalDateTime.now()
                    delay(1000L - System.currentTimeMillis() % 1000L)
                }
            }
            val battery by produceState<Pair<Int?, Boolean>>(null to false) {
                while (true) {
                    val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
                    value = (if (level >= 0) level * 100 / scale else null) to plugged
                    delay(30_000)
                }
            }
            val pager = rememberPagerState(initialPage = prefs.getInt(KEY_STYLE, 0).coerceIn(0, StandbyClocks.COUNT - 1)) { StandbyClocks.COUNT }
            var showUi by remember { mutableStateOf(true) }
            LaunchedEffect(pager) {
                snapshotFlow { pager.currentPage }.collect {
                    prefs.edit().putInt(KEY_STYLE, it).apply()
                    showUi = true
                }
            }
            LaunchedEffect(showUi, pager.currentPage) {
                if (showUi) {
                    delay(2500)
                    showUi = false
                }
            }
            val uiAlpha by animateFloatAsState(if (showUi) 1f else 0f, tween(300), label = "ui")
            Box(Modifier.fillMaxSize().background(Color.Black).clickable(remember { MutableInteractionSource() }, null) { showUi = !showUi }) {
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    // 넘길 때 옆 시계는 살짝 작아지고 흐려지고 초점이 풀리며 들어온다(겹쳐 보이는 깊이감)
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            val off = kotlin.math.abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceAtMost(1f)
                            alpha = 1f - off * 0.7f
                            scaleX = 1f - off * 0.08f
                            scaleY = 1f - off * 0.08f
                            val blur = off * 28f
                            renderEffect = if (blur > 0.5f) android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect() else null
                        }
                    ) { StandbyClock(page, now, battery.first, battery.second) }
                }
                if (uiAlpha > 0.01f) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(StandbyClocks.NAMES[pager.currentPage], color = Color.White.copy(alpha = uiAlpha), fontSize = 15.sp)
                        Spacer(Modifier.height(Gap.Item))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StandbyDots(pager.currentPage, uiAlpha)
                            Text(
                                stringResource(R.string.lab_standby_finish),
                                color = Color.White.copy(alpha = uiAlpha),
                                fontSize = 14.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f * uiAlpha))
                                    .clickable { TentDuo.stop(this@StandbyActivity, "끝내기 버튼") }.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
        current = this
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CHECK = "check"
        const val EXTRA_PATTERN = "pattern"
        private const val PREF = "standby"
        private const val KEY_STYLE = "style"

        /** 고른 시계 스타일 번호(앰비언트 모드 화면의 썸네일에서도 고른다). */
        fun style(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_STYLE, 0).coerceIn(0, StandbyClocks.COUNT - 1)
        fun setStyle(context: Context, i: Int) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_STYLE, i).apply()

        /** 떠 있는 스탠바이 화면(텐트 모드를 끝낼 때 닫는다). */
        @Volatile
        var current: StandbyActivity? = null
            private set
    }
}

/** 방향 확인: 네 가장자리를 빨강(위)·파랑(아래)·초록(왼쪽)·노랑(오른쪽)으로 칠하고 글자를 띄운다. 안쪽 빛이 같은 색으로 어디에 비치는지 본다. */
@androidx.compose.runtime.Composable
private fun DirectionCheck(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.18f).background(Color(0xFFFF2020)).align(Alignment.TopCenter)) { Text(stringResource(R.string.lab_direction_top), Modifier.align(Alignment.Center), color = Color.White, fontSize = 40.sp) }
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.18f).background(Color(0xFF2050FF)).align(Alignment.BottomCenter)) { Text(stringResource(R.string.lab_direction_bottom), Modifier.align(Alignment.Center), color = Color.White, fontSize = 40.sp) }
        Box(Modifier.fillMaxWidth(0.12f).fillMaxHeight(0.6f).background(Color(0xFF20D040)).align(Alignment.CenterStart)) { Text(stringResource(R.string.lab_direction_left), Modifier.align(Alignment.Center), color = Color.White, fontSize = 30.sp) }
        Box(Modifier.fillMaxWidth(0.12f).fillMaxHeight(0.6f).background(Color(0xFFFFD020)).align(Alignment.CenterEnd)) { Text(stringResource(R.string.lab_direction_right), Modifier.align(Alignment.Center), color = Color.Black, fontSize = 30.sp) }
        Text(
            stringResource(R.string.lab_direction_check_body),
            Modifier.align(Alignment.Center).clickable { onClose() },
            color = Color.White,
            fontSize = 18.sp
        )
    }
}

/** 반사 시험 무늬(위 절반이 패널에 비치는 원본). 6은 1.5초마다 색이 확 바뀌는 무늬(반응 지연 실측용, 바뀐 시각을 logcat GOATAMB에 남긴다). */
@androidx.compose.runtime.Composable
private fun ReflectionPattern(kind: Int) {
    if (kind == 14) {
        // 14: 계속 움직이는 화면 — 색상환이 6초에 한 바퀴 천천히 돈다(영상처럼 쉬지 않고 바뀜). 시간 보간 '전환 뒤 멈춤'이 0에 가까운지 본다
        val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "p14")
        val t: Float by tr.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(6000, easing = androidx.compose.animation.core.LinearEasing)),
            label = "hue"
        )
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.hsv(t, 0.8f, 0.9f), Color.hsv((t + 120f) % 360f, 0.8f, 0.9f), Color.hsv((t + 240f) % 360f, 0.8f, 0.9f)))))
        return
    }
    if (kind == 6) {
        val colors = listOf(Color(0xFFE02020), Color(0xFF20B040), Color(0xFF2040E0), Color(0xFFE0C020))
        var i by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1500)
                i = (i + 1) % colors.size
                android.util.Log.i("GOATAMB", "pattern-change $i")
            }
        }
        Box(Modifier.fillMaxSize().background(colors[i]))
        return
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize().background(Color.Black)) {
        val w = size.width
        val h = size.height
        when (kind) {
            1 -> {
                drawRect(Color(0xFFE02020), size = androidx.compose.ui.geometry.Size(w / 2, h))
                drawRect(Color(0xFF2040E0), topLeft = androidx.compose.ui.geometry.Offset(w / 2, 0f), size = androidx.compose.ui.geometry.Size(w / 2, h))
                var x = 0f
                while (x < w) {
                    drawRect(Color(0xFF101010), topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(w / 32f, h))
                    x += w / 8f
                }
            }
            2 -> drawRect(Color(0xFFF0F0F0), size = androidx.compose.ui.geometry.Size(w, h / 4))
            3 -> {
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF007AFF), Color(0xFFAF52DE))))
                drawCircle(Color(0xFFFFFFFF), h * 0.12f, androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.3f))
            }
            5 -> drawRect(Color.White)
            // 7: X축 정합 측정용 — 검은 바탕에 10·30·50·70·90% 자리의 흰 세로 표식(폭 2%). 반사에서 같은 자리에 맺히는지 px로 잰다
            7 -> for (f in listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)) {
                drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w * (f - 0.01f), 0f), size = androidx.compose.ui.geometry.Size(w * 0.02f, h))
            }
            // 8: 네 색 띠(빨강·초록·파랑·노랑, 각 25%) — 색 경계가 반사에서 같은 x에 오는지 본다
            8 -> listOf(Color(0xFFE02020), Color(0xFF20B040), Color(0xFF2040E0), Color(0xFFE0C020)).forEachIndexed { i, c ->
                drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(w * i / 4f, 0f), size = androidx.compose.ui.geometry.Size(w / 4f, h))
            }
            // 10: 레터박스 — 위 30%만 영상(네 색 띠 + 흰 표식), 그 아래는 검은 띠(유튜브 세로 화면처럼). 반사가 영상 아래 끝부터 또렷하게 시작하는지 본다
            10 -> {
                listOf(Color(0xFFE02020), Color(0xFF20B040), Color(0xFF2040E0), Color(0xFFE0C020)).forEachIndexed { i, c ->
                    drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(w * i / 4f, 0f), size = androidx.compose.ui.geometry.Size(w / 4f, h * 0.3f))
                }
                for (f in listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)) {
                    drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w * (f - 0.01f), 0f), size = androidx.compose.ui.geometry.Size(w * 0.02f, h * 0.3f))
                }
            }
            // 11: 격자 — 화면 폭의 1/12 간격 흰 세로선과 가로선. 반사에서 세로선 간격이 그대로 유지되는지(늘어나거나 줄지 않는지) 본다
            11 -> {
                var x = w / 24f
                while (x < w) {
                    drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(x - w * 0.006f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.012f, h))
                    x += w / 12f
                }
                var y = h / 24f
                while (y < h) {
                    drawRect(Color(0xFF808080), topLeft = androidx.compose.ui.geometry.Offset(0f, y - 2f), size = androidx.compose.ui.geometry.Size(w, 4f))
                    y += h / 12f
                }
            }
            // 12: 따뜻한 장면(주황) / 13: 차가운 장면(파랑) — 카드 틴트가 장면 색을 따라가는지 본다
            12 -> drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFF9A3C), Color(0xFFB0441A))))
            13 -> drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3C8CFF), Color(0xFF1A2E8A))))
            else -> Unit
        }
    }
}

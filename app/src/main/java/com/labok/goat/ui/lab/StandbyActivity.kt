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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
                                "끝내기",
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
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.18f).background(Color(0xFFFF2020)).align(Alignment.TopCenter)) { Text("위", Modifier.align(Alignment.Center), color = Color.White, fontSize = 40.sp) }
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.18f).background(Color(0xFF2050FF)).align(Alignment.BottomCenter)) { Text("아래", Modifier.align(Alignment.Center), color = Color.White, fontSize = 40.sp) }
        Box(Modifier.fillMaxWidth(0.12f).fillMaxHeight(0.6f).background(Color(0xFF20D040)).align(Alignment.CenterStart)) { Text("왼쪽", Modifier.align(Alignment.Center), color = Color.White, fontSize = 30.sp) }
        Box(Modifier.fillMaxWidth(0.12f).fillMaxHeight(0.6f).background(Color(0xFFFFD020)).align(Alignment.CenterEnd)) { Text("오른쪽", Modifier.align(Alignment.Center), color = Color.Black, fontSize = 30.sp) }
        Text(
            "안쪽 화면 빛에서 빨강·파랑·초록·노랑이 어디에 보이는지 알려 주세요.\n누르면 끝납니다.",
            Modifier.align(Alignment.Center).clickable { onClose() },
            color = Color.White,
            fontSize = 18.sp
        )
    }
}

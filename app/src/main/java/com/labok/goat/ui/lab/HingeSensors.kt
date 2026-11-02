package com.labok.goat.ui.lab

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.labok.goat.data.lab.FineHinge
import kotlinx.coroutines.delay

/** 지금 화면이 세로인지(플렉스 모드를 켜는 조건과 같다 — 자이로·중력은 쓰지 않는다, 11-02). */
@Composable
internal fun rememberScreenPortrait(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE

/**
 * 화면에서 직접 읽는 힌지 각도([on]일 때만): 기본 센서(0·90·180)와 Shizuku 세밀한 각도([com.labok.goat.data.lab.FineHinge]).
 * 세밀한 값이 오면 그걸 쓴다.
 */
@Composable
internal fun rememberHingeAngle(on: Boolean): Float? {
    val context = LocalContext.current
    var coarse by remember { mutableStateOf<Float?>(null) }
    var fineAngle by remember { mutableStateOf<Float?>(null) }
    var fineAt by remember { mutableLongStateOf(0L) }
    // 세밀한 값이 끊겼는지 다시 보려고 0.5초마다 한 번 깨운다
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(on) {
        while (on) {
            delay(500)
            tick++
        }
    }
    DisposableEffect(on) {
        if (!on) {
            coarse = null
            fineAngle = null
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(SensorManager::class.java)
        val hinge = sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                coarse = e.values[0]
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (hinge != null) sm.registerListener(l, hinge, SensorManager.SENSOR_DELAY_UI)
        val fine = FineHinge { v ->
            fineAngle = v
            fineAt = SystemClock.elapsedRealtime()
        }
        fine.start()
        onDispose {
            sm?.unregisterListener(l)
            fine.stop()
        }
    }
    // 빨리 펴거나 닫으면 세밀한 로그가 중간 값에서 멈출 수 있다. 1초 넘게 끊겼고 기본 센서가 끝값(0°·180°)이면 기본 센서를 믿는다
    tick
    val c = coarse
    val stale = SystemClock.elapsedRealtime() - fineAt >= 1000
    return if (c != null && stale && (c <= 0f || c >= 180f)) c else fineAngle ?: c
}

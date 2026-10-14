package com.labok.goat.ui.lab

import android.app.Activity
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.data.lab.FineHinge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 지금 접는 선이 가로로 놓였는지(화면에서 직접 중력을 읽는다, [on]일 때만). 판정은 서비스와 같다([AutoFlex.isHorizontal]).
 * 처음 한 번 창이 알려 주는 접는 선 방향과 화면 회전으로 힌지 축(기본 방향에서 세로인지 가로인지)을 확인해 저장해 둔다.
 */
@Composable
internal fun rememberHingeHorizontal(on: Boolean): Boolean {
    val context = LocalContext.current
    var horizontal by remember { mutableStateOf(true) }
    val activity = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() }
    LaunchedEffect(activity, on) {
        if (!on || activity == null) return@LaunchedEffect
        val info = WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).first()
        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() ?: return@LaunchedEffect

        @Suppress("DEPRECATION")
        val rot = activity.windowManager.defaultDisplay.rotation
        val upright = rot == Surface.ROTATION_0 || rot == Surface.ROTATION_180
        AutoFlex.setHingeAlongY(context, (fold.orientation == FoldingFeature.Orientation.VERTICAL) == upright)
    }
    DisposableEffect(on) {
        if (!on) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                horizontal = AutoFlex.isHorizontal(e.values[0], e.values[1], e.values[2], AutoFlex.hingeAlongY(context))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm?.unregisterListener(l) }
    }
    return horizontal
}

/**
 * 화면에서 직접 읽는 힌지 각도([on]일 때만): 기본 센서(0·90·180)와 Shizuku 세밀한 각도([com.labok.goat.data.lab.FineHinge]).
 * 세밀한 값이 오면 그걸 쓴다.
 */
@Composable
internal fun rememberHingeAngle(on: Boolean): Float? {
    val context = LocalContext.current
    var coarse by remember { mutableStateOf<Float?>(null) }
    var fineAngle by remember { mutableStateOf<Float?>(null) }
    var fineAt by remember { mutableStateOf(0L) }
    // 세밀한 값이 끊겼는지 다시 보려고 0.5초마다 한 번 깨운다
    var tick by remember { mutableStateOf(0) }
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

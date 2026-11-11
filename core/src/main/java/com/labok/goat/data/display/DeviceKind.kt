package com.labok.goat.data.display

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * 이 기기가 접는 폰(폴드)인지. 폴드는 접힘 각도를 재는 힌지 센서가 있다(갤럭시 Z 폴드·플립, 픽셀 폴드).
 * 폴드가 아니면 화면이 하나라, 두 화면을 맞추는 기능(내부 화면 보정·두 화면 미리보기·크기 테스트·접기 감시)은 숨긴다.
 */
object DeviceKind {
    @Volatile private var cached: Boolean? = null

    fun isFoldable(context: Context): Boolean = cached ?: run {
        val pm = context.packageManager
        val sm = context.getSystemService(SensorManager::class.java)
        val hinge = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE) || sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
        hinge.also { cached = it }
    }
}

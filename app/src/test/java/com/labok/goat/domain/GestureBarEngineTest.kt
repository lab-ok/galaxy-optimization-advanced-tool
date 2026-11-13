package com.labok.goat.domain

import android.app.AppOpsManager
import android.os.Process
import org.robolectric.shadows.ShadowSettings
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.labok.goat.data.rotate.PowerRotate
import com.labok.goat.data.rotate.ShakeRotate
import com.labok.goat.data.rotate.PowerRotateEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GestureBarEngineTest {
    private fun context(normal: Boolean = false): Context = ApplicationProvider.getApplicationContext<Application>().also {
        if (normal) shadowOf(it).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        else shadowOf(it).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(it.getSystemService(AppOpsManager::class.java)).setMode(AppOpsManager.OPSTR_WRITE_SETTINGS,
            Process.myUid(), it.packageName, if (normal) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED)
        ShadowSettings.setCanDrawOverlays(true)
        it.getSharedPreferences("power_rotate", 0).edit().clear().putBoolean("on", true).commit()
        PowerRotate.detection.value = PowerRotate.Detection()
    }
    private fun event(e: PowerRotateEngine, action: Int, at: Long, y: Float = 100f) {
        val motion = MotionEvent.obtain(at, at, action, 100f, y, 0)
        try { e.touch(motion) } finally { motion.recycle() }
    }
    private fun tap(e: PowerRotateEngine, at: Long) { event(e, 0, at); event(e, 1, at + 50) }

    @Test fun screenTransitionsNoLongerTriggerRotation() {
        val c = context(); var calls = 0; val e = PowerRotateEngine(c) { calls++; true }
        try {
            e.refresh()
            repeat(3) { c.sendBroadcast(Intent(if (it % 2 == 0) Intent.ACTION_SCREEN_OFF else Intent.ACTION_SCREEN_ON)) }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, calls); assertEquals(0, PowerRotate.detection.value.events)
        } finally { e.stop() }
    }
    @Test fun threeTapsTriggerWithoutShizukuAndStopRemovesInput() {
        val c = context(); var calls = 0; val e = PowerRotateEngine(c) { calls++; true }
        try { e.refresh(); tap(e, 0); tap(e, 200); tap(e, 400); assertEquals(1, calls)
            e.stop(); repeat(3) { tap(e, 600L + it * 200) }; assertEquals(1, calls)
        } finally { e.stop() }
    }
    @Test fun swipeCancelsTwoPendingTaps() {
        val c = context(); var calls = 0; val e = PowerRotateEngine(c) { calls++; true }
        try { e.refresh(); tap(e, 0); tap(e, 200); event(e, 0, 400); event(e, 2, 420, 20f); event(e, 1, 450)
            tap(e, 600); assertEquals(0, calls)
        } finally { e.stop() }
    }
    @Test fun windowChangeResetsButOrdinaryRefreshPreservesSequence() {
        val c = context(); var calls = 0; val e = PowerRotateEngine(c) { calls++; true }
        try { e.refresh(); tap(e, 0); tap(e, 200); e.refresh(); tap(e, 400); assertEquals(1, calls)
            tap(e, 600); tap(e, 800); PowerRotate.setWindow(c, PowerRotate.Window.LONG); e.refresh(); tap(e, 1000)
            assertEquals(1, calls)
        } finally { e.stop() }
    }
    @Test fun missingOrRevokedPermissionPreventsAction() {
        val c = context(); var calls = 0; val e = PowerRotateEngine(c) { calls++; true }
        try { e.refresh(); tap(e, 0); tap(e, 200); shadowOf(c as Application).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
            tap(e, 400); assertEquals(0, calls); assertEquals(PowerRotate.Status.WAITING, PowerRotate.status.value)
        } finally { e.stop() }
    }
    @Test fun exceptionIsRecordedAndNextGestureStillWorks() {
        val c = context(); var calls = 0; val e = PowerRotateEngine(c) { if (++calls == 1) error("device write failed") else true }
        try { e.refresh(); repeat(3) { tap(e, it * 200L) }; assertEquals(false, PowerRotate.detection.value.actionSucceeded)
            repeat(3) { tap(e, 1000L + it * 200L) }; assertEquals(true, PowerRotate.detection.value.actionSucceeded)
        } finally { e.stop() }
    }
    @Test fun normalUserSettingsGrantWorksWithoutSecurePermissionOrAccessibility() {
        val c = context(normal = true); var calls = 0
        assertFalse(ShakeRotate.canWrite(c))
        assertTrue(PowerRotate.canWriteRotation(c))
        val e = PowerRotateEngine(c) { calls++; true }
        try { e.refresh(); repeat(3) { tap(e, it * 200L) }; assertEquals(1, calls) }
        finally { e.stop() }
    }
    @Test fun revokingOverlayPermissionCancelsTheSequenceAndRegrantStartsFresh() {
        val c = context(); var calls = 0; val e = PowerRotateEngine(c) { calls++; true }
        try {
            e.refresh(); tap(e, 0); tap(e, 200); ShadowSettings.setCanDrawOverlays(false); tap(e, 400)
            assertEquals(0, calls); assertEquals(PowerRotate.Status.WAITING, PowerRotate.status.value)
            ShadowSettings.setCanDrawOverlays(true); e.refresh(); tap(e, 600); assertEquals(0, calls)
            tap(e, 800); tap(e, 1000); assertEquals(1, calls)
        } finally { e.stop() }
    }
}

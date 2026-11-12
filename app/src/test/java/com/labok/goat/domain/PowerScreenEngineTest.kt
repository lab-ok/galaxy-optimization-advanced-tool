package com.labok.goat.domain

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.labok.goat.data.rotate.PowerRotate
import com.labok.goat.data.rotate.PowerRotateEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PowerScreenEngineTest {
    private fun context() = ApplicationProvider.getApplicationContext<Context>()
    private fun transition(context: Context, on: Boolean) {
        context.sendBroadcast(Intent(if (on) Intent.ACTION_SCREEN_ON else Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun receivesScreenEventsWithoutAnyShizukuConnectionAndStopsCleanly() {
        val context = context()
        shadowOf(context as Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putBoolean("on", true).commit()
        val previous = PowerRotate.status.value
        var actions = 0
        val engine = PowerRotateEngine(context) { actions++; true }
        val initial = context.getSystemService(PowerManager::class.java).isInteractive
        try {
            engine.refresh()
            assertEquals(PowerRotate.Status.LISTENING, PowerRotate.status.value)
            transition(context, !initial)
            transition(context, initial)
            transition(context, !initial)
            assertEquals(1, actions)
            engine.stop()
            transition(context, initial)
            transition(context, !initial)
            transition(context, initial)
            assertEquals(1, actions)
        } finally {
            engine.stop()
            PowerRotate.status.value = previous
        }
    }

    @Test fun missingSettingsPermissionPreventsDetection() {
        val context = context()
        shadowOf(context as Application).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        val previous = PowerRotate.status.value
        val engine = PowerRotateEngine(context) { fail("Must not rotate without permission"); false }
        try {
            engine.refresh()
            assertEquals(PowerRotate.Status.WAITING, PowerRotate.status.value)
        } finally { engine.stop(); PowerRotate.status.value = previous }
    }

    @Test fun disablingBeforeTheOneShotDelayCancelsThePendingAction() {
        val context = context()
        shadowOf(context as Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        val power = context.getSystemService(PowerManager::class.java)
        shadowOf(power).setIsInteractive(false)
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit()
            .putBoolean("on", true).putString("mode", "ONCE").commit()
        val previous = PowerRotate.status.value
        var actions = 0
        val engine = PowerRotateEngine(context) { actions++; true }
        try {
            engine.refresh()
            transition(context, true)
            transition(context, false)
            transition(context, true)
            assertEquals(0, actions)
            engine.stop()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals(0, actions)
        } finally { engine.stop(); PowerRotate.status.value = previous }
    }

    @Test fun refreshingTheSameWindowPreservesAnInProgressGesture() {
        val context = context()
        shadowOf(context as Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putBoolean("on", true).commit()
        var actions = 0
        val engine = PowerRotateEngine(context) { actions++; true }
        val initial = context.getSystemService(PowerManager::class.java).isInteractive
        try {
            engine.refresh()
            transition(context, !initial)
            transition(context, initial)
            engine.refresh()
            transition(context, !initial)
            assertEquals(1, actions)
        } finally { engine.stop() }
    }

    @Test fun changingTheWindowResetsOnlyTheCurrentSequence() {
        val context = context()
        shadowOf(context as Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putBoolean("on", true).commit()
        var actions = 0
        val engine = PowerRotateEngine(context) { actions++; true }
        val initial = context.getSystemService(PowerManager::class.java).isInteractive
        try {
            engine.refresh()
            transition(context, !initial)
            transition(context, initial)
            PowerRotate.setWindow(context, PowerRotate.Window.LONG)
            engine.refresh()
            transition(context, !initial)
            assertEquals(0, actions)
            transition(context, initial)
            transition(context, !initial)
            assertEquals(1, actions)
            assertTrue(PowerRotate.diagnose(context).contains("windowMs=4000"))
        } finally { engine.stop() }
    }

    @Test fun configuredTimingIsUsedAndActionExceptionsAreReportedWithoutLosingTheListener() {
        val context = context()
        shadowOf(context as Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putBoolean("on", true).putString("window", "MEDIUM").commit()
        val engine = PowerRotateEngine(context) { throw IllegalStateException("device write failed") }
        val initial = context.getSystemService(PowerManager::class.java).isInteractive
        try {
            engine.refresh()
            transition(context, !initial)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
            transition(context, initial)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
            transition(context, !initial)
            assertEquals(PowerRotate.Status.LISTENING, PowerRotate.status.value)
            assertEquals(false, PowerRotate.detection.value.actionSucceeded)
            assertTrue(PowerRotate.diagnose(context).contains("device write failed"))
        } finally { engine.stop() }
    }

    @Test fun duplicateEventsRemainVisibleInBoundedDiagnosticsWithoutTriggeringActions() {
        val context = context()
        shadowOf(context as Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putBoolean("on", true).commit()
        val before = PowerRotate.detection.value.events
        val engine = PowerRotateEngine(context) { fail("Duplicates must not rotate"); false }
        val initial = context.getSystemService(PowerManager::class.java).isInteractive
        try {
            engine.refresh()
            repeat(25) { transition(context, initial) }
            assertEquals(before + 25, PowerRotate.detection.value.events)
            assertEquals(20, PowerRotate.detection.value.history.size)
            assertEquals(ScreenTripleTransition.Decision.DUPLICATE, PowerRotate.detection.value.decision)
        } finally { engine.stop() }
    }
}

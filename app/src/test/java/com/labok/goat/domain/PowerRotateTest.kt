package com.labok.goat.domain

import android.Manifest
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.labok.goat.data.rotate.PowerRotate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PowerRotateTest {
    @Test fun togglesFromActualSystemStateAndLocksTheCapturedDirection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        assertTrue(PowerRotate.toggle(context, 3))
        assertEquals(0, Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION))
        assertEquals(3, Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION))
        assertTrue(PowerRotate.toggle(context, 0))
        assertEquals(1, Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION))
        // A Quick Settings change must be respected by the next triple press.
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        assertTrue(PowerRotate.toggle(context, 2))
        assertEquals(1, Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION))
    }

    @Test fun missingPermissionLeavesRotationUntouched() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        assertFalse(PowerRotate.toggle(context, 2))
        assertEquals(1, Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION))
    }

    @Test fun actionChoiceIsExclusiveAndSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(PowerRotate.Mode.TOGGLE, PowerRotate.mode(context))
        PowerRotate.setMode(context, PowerRotate.Mode.ONCE)
        assertEquals(PowerRotate.Mode.ONCE, PowerRotate.mode(context))
        PowerRotate.setMode(context, PowerRotate.Mode.TOGGLE)
        assertEquals(PowerRotate.Mode.TOGGLE, PowerRotate.mode(context))
    }

    @Test fun unknownSavedActionFallsBackToToggle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putString("mode", "removed-mode").commit()
        assertEquals(PowerRotate.Mode.TOGGLE, PowerRotate.mode(context))
    }

    @Test fun manualRotationSettlesBackToLockedWithoutEnablingShakeDetection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        val engine = com.labok.goat.data.rotate.ShakeEngine(context) { false }
        assertTrue(engine.rotateOnce())
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        assertEquals(0, Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION))
        assertFalse(com.labok.goat.data.rotate.ShakeRotate.enabled(context))
    }


    @Test fun anEstablishedReaderDoesNotRequireShizukuToRemainConnected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = PowerRotate.status.value
        context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putBoolean("on", true).commit()
        try {
            PowerRotate.status.value = PowerRotate.Status.LISTENING
            assertFalse(PowerRotate.needsShizuku(context))
            PowerRotate.status.value = PowerRotate.Status.WAITING
            assertTrue(PowerRotate.needsShizuku(context))
            context.getSharedPreferences("power_rotate", Context.MODE_PRIVATE).edit().putBoolean("on", false).commit()
            assertFalse(PowerRotate.needsShizuku(context))
        } finally {
            PowerRotate.status.value = previous
        }
    }

}

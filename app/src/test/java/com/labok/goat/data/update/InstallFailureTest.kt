package com.labok.goat.data.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallFailureTest {
    @Test
    fun explainsCommonFailuresInKorean() {
        assertTrue(InstallFailure.explain(PackageInstaller.STATUS_FAILURE_CONFLICT, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match").contains("서명이 달라"))
        assertTrue(InstallFailure.explain(null, "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]").contains("저장 공간"))
        assertTrue(InstallFailure.explain(PackageInstaller.STATUS_FAILURE_ABORTED, null).contains("취소"))
        assertTrue(InstallFailure.explain(PackageInstaller.STATUS_FAILURE_BLOCKED, "").contains("보안"))
        assertTrue("모르는 코드는 원문을 남긴다", InstallFailure.explain(1, "WEIRD_CODE").contains("WEIRD_CODE"))
    }

    @Test
    fun picksRoParticleByLastDigit() {
        assertEquals("GOAT 1.6.0으로 업데이트했습니다", UpdateNotice.doneText("1.6.0"))
        assertEquals("GOAT 1.6.1로 업데이트했습니다", UpdateNotice.doneText("1.6.1"))
        assertEquals("GOAT 1.7.3으로 업데이트했습니다", UpdateNotice.doneText("1.7.3"))
    }

    @Test
    fun relaunchWatcherIsDetachedAndQuoted() {
        val cmd = Updater.relaunchAfter("com.labok.goat", "1.6.0")
        assertEquals(
            "setsid sh -c 'for i in \$(seq 60); do sleep 1; dumpsys package com.labok.goat | grep -q \"versionName=1.6.0\$\" && " +
                "{ am start -n com.labok.goat/.ui.MainActivity; exit 0; }; done' </dev/null >/dev/null 2>&1 & ",
            cmd
        )
    }
}

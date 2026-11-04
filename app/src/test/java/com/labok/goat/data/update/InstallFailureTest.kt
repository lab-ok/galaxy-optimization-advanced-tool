package com.labok.goat.data.update

import android.content.pm.PackageInstaller
import com.labok.goat.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InstallFailureTest {
    // 화면 문구가 리소스로 옮겨져 JVM 테스트에서는 xml을 직접 읽어 넣는다
    @Before
    fun strings() = TestStrings.install()

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

    @Test
    fun selfUpdateBlockedByUnknownSourceAsksForPermission() {
        // 11-03 사용자 메일(갤럭시 S26, Android 17): 시스템 설치 창이 '출처를 알 수 없는 앱 설치'가 꺼져 있어 막았다
        assertEquals(InstallFailure.UNKNOWN_SOURCE, InstallFailure.explain(3, "INSTALL_FAILED_ABORTED: Self update is blocked by unknown source package"))
        assertEquals(InstallFailure.UNKNOWN_SOURCE, InstallFailure.explain(3, "INSTALL_FAILED_ABORTED: User rejected permissions"))
    }
}

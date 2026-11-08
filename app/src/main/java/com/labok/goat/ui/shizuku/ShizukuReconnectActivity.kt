package com.labok.goat.ui.shizuku

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.MainActivity

/**
 * 'Shizuku 연결이 끊겼습니다' 알림을 눌렀을 때 거치는 빈 화면. 무선 디버깅이 꺼져 있으면 GOAT가 켜 두고(이미 받은 보안 설정 쓰기 권한),
 * Shizuku 앱을 연다. 사용자는 Shizuku에서 [시작]만 누르면 된다. Shizuku가 없으면 GOAT의 Shizuku 안내로 간다.
 * 알림에서 서비스·수신기를 거쳐 화면을 여는 것은 Android 12부터 막혀 있어 화면으로 받는다.
 */
class ShizukuReconnectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debugOn = ShizukuAccess.wirelessDebugOn(this)
        val turnedOn = !debugOn && ShizukuAccess.enableWirelessDebug(this)
        val shizuku = packageManager.getLaunchIntentForPackage(ShizukuAccess.PACKAGE)
        LogRepository.from(this).add("Shizuku 다시 연결: 무선 디버깅 " + (if (debugOn) "이미 켜짐" else if (turnedOn) "켬" else "켜지 못함") + " · " + if (shizuku != null) "Shizuku 열기" else "Shizuku 없음, 안내 열기")
        startActivity((shizuku ?: Intent(this, MainActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

package com.labok.goat.ui.shizuku

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.MainActivity
import com.labok.goat.ui.text.Copy

/**
 * 'Shizuku 연결이 끊겼습니다' 알림을 눌렀을 때 거치는 빈 화면(11-08 소장님 "개발자 옵션까지 한 번에 다시 설정할 수 있게").
 * GOAT가 이미 받은 보안 설정 쓰기 권한으로 개발자 옵션·USB 디버깅·무선 디버깅을 켜고 Shizuku 앱을 연다. 사용자는 Shizuku에서 [시작]만 누르면 된다.
 * 무선 디버깅은 Wi-Fi에 연결돼 있어야 켜지므로, Wi-Fi가 없으면 Wi-Fi 설정부터 연다. 권한이 없거나 Shizuku가 없으면 GOAT의 Shizuku 안내로 간다.
 * 알림에서 서비스·수신기를 거쳐 화면을 여는 것은 Android 12부터 막혀 있어 화면으로 받는다.
 */
class ShizukuReconnectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = LogRepository.from(this)
        val canWrite = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        val shizuku = packageManager.getLaunchIntentForPackage(ShizukuAccess.PACKAGE)
        when {
            !canWrite || shizuku == null -> {
                log.add("Shizuku 다시 연결: " + (if (!canWrite) "보안 설정 권한 없음" else "Shizuku 없음") + ", 안내 열기")
                open(Intent(this, MainActivity::class.java))
            }
            !onWifi() -> {
                log.add("Shizuku 다시 연결: Wi-Fi 연결 없음, Wi-Fi 설정 열기")
                Toast.makeText(this, Copy.text(R.string.shizuku_need_wifi), Toast.LENGTH_LONG).show()
                open(Intent(Settings.Panel.ACTION_WIFI))
            }
            else -> {
                val ok = ShizukuAccess.enableWirelessDebug(this)
                log.add("Shizuku 다시 연결: 개발자 옵션·무선 디버깅 " + (if (ok) "켬" else "켜지 못함") + " · Shizuku 열기")
                open(shizuku)
            }
        }
        finish()
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Wi-Fi에 연결돼 있는지. 확인하지 못하면 켜기를 시도한다(true). */
    private fun onWifi(): Boolean = runCatching {
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }.getOrDefault(true)
}

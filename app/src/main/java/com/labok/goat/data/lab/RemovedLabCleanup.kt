package com.labok.goat.data.lab

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

/**
 * 1.49.0에서 없앤 실험실 기능(앱 폭 좁히기·맨 위로 가기)이 남긴 설정을 지운다.
 * 앱 폭 좁히기는 앱마다 호환성 설정과 여백 모양을 걸어 두어서, 지우지 않으면 그 앱이 계속 좁게 뜬다. Shizuku가 연결될 때 한 번 정리한다.
 * 화면 모드는 사용자가 고른 값을 그대로 둔다(삼성 설정 앱에서 바꿀 수 있다).
 */
object RemovedLabCleanup {
    private const val NARROW_PREF = "narrow_apps"
    private const val GESTURE_PREF = "status_gestures"
    private const val GESTURE_SERVICE = "com.labok.goat.data.lab.StatusGestureService"

    private val binder = Shizuku.OnBinderReceivedListener { cleanupAsync?.invoke() }

    @Volatile private var cleanupAsync: (() -> Unit)? = null

    /** 앱 시작 때 부른다. 남은 설정이 없으면 아무것도 하지 않는다. */
    fun watch(context: Context) {
        val app = context.applicationContext
        dropGestureService(app)
        if (narrowPrefs(app).all.isEmpty()) return
        cleanupAsync = { Thread { clearNarrow(app) }.start() }
        Shizuku.addBinderReceivedListenerSticky(binder)
    }

    /** 맨 위로 가기·꾹 눌러 좁히기가 켠 접근성 서비스를 목록에서 뺀다(서비스는 앱에서 없어졌다). */
    private fun dropGestureService(context: Context) {
        val p = context.getSharedPreferences(GESTURE_PREF, Context.MODE_PRIVATE)
        if (p.all.isEmpty()) return
        val gone = ComponentName(context.packageName, GESTURE_SERVICE)
        runCatching {
            val r = context.contentResolver
            val list = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':').filter { it.isNotBlank() }
            val keep = list.filter { ComponentName.unflattenFromString(it) != gone }
            if (keep.size != list.size) Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, keep.joinToString(":"))
        }
        p.edit().clear().apply()
    }

    /** 좁혀 둔 앱의 호환성 설정과 여백 모양을 되돌린다(작업 스레드). Shizuku 셸을 못 쓰면 다음 연결 때 다시 한다. */
    private fun clearNarrow(context: Context) {
        val p = narrowPrefs(context)
        if (p.all.isEmpty()) return
        if (ShizukuAccess.shell("wm reset-letterbox-style")?.ok != true) return
        val apps = p.getStringSet("apps", emptySet()).orEmpty() + p.getStringSet("global_applied", emptySet()).orEmpty() +
            p.all.keys.filter { it.startsWith("ratio:") }.map { it.removePrefix("ratio:") }
        apps.forEach { ShizukuAccess.clearCompat(it) }
        p.edit().clear().apply()
        LogRepository.from(context).add("없앤 실험실 기능 정리: 앱 폭 좁히기 설정 ${apps.size}개 되돌림")
    }

    private fun narrowPrefs(context: Context) = context.getSharedPreferences(NARROW_PREF, Context.MODE_PRIVATE)
}

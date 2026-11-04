package com.labok.goat.ui.about

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.labok.goat.R
import com.labok.goat.data.appsize.AppUsage
import com.labok.goat.data.lab.VolumeRotate
import com.labok.goat.ui.text.Copy

/** 앱이 쓰는 권한과 받는 방법. 최종 APK의 권한 목록(aapt2 dump permissions)과 맞춘다. */
internal object AppPermissions {

    enum class How(private val labelRes: Int) {
        ADB(R.string.about_perm_how_adb),
        USER(R.string.about_perm_how_user),
        INSTALL(R.string.about_perm_how_install),
        GOAT(R.string.about_perm_how_goat);

        val label: String get() = Copy.text(labelRes)
    }

    data class Item(
        val permission: String,
        val title: String,
        val why: String,
        val how: How,
        /** 이 기기에서 쓰이지 않는 권한(예: Android 12의 알림 권한)이면 false. */
        val applies: Boolean = true,
        /** 없어도 다른 길로 되는 권한(예: Shizuku가 있으면 필요 없는 앱 설치). 없을 때 '필요함' 대신 '선택'으로 보인다. */
        val optional: Boolean = false
    )

    /** Shizuku API를 쓰는 권한(Shizuku 라이브러리가 매니페스트에 넣는다). Shizuku의 허용 창에서 받는다. */
    private const val SHIZUKU_API = "moe.shizuku.manager.permission.API_V23"

    /** 접근성 서비스(볼륨 버튼 회전)를 가리키는 이름. 매니페스트 권한이 아니라 서비스 등록이라 [granted]에서 따로 본다. */
    private const val ACCESSIBILITY = "accessibility"

    // 권한 이름은 컴파일 때 문자열로 박히는 상수라 낮은 버전에서도 안전하다. 해당 버전 미만은 applies로 거른다.

    @get:SuppressLint("InlinedApi")
    val all: List<Item> get() = listOf(
        Item(
            Manifest.permission.WRITE_SECURE_SETTINGS,
            Copy.text(R.string.about_perm_secure_settings_title),
            Copy.text(R.string.about_perm_secure_settings_why),
            How.ADB
        ),
        Item(
            Manifest.permission.POST_NOTIFICATIONS,
            Copy.text(R.string.about_perm_notifications_title),
            Copy.text(R.string.about_perm_notifications_why),
            How.USER,
            applies = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ),
        Item(
            SHIZUKU_API,
            Copy.text(R.string.about_perm_shizuku_title),
            Copy.text(R.string.about_perm_shizuku_why),
            How.USER
        ),
        Item(
            Manifest.permission.PACKAGE_USAGE_STATS,
            Copy.text(R.string.about_perm_usage_stats_title),
            Copy.text(R.string.about_perm_usage_stats_why),
            How.USER
        ),
        // 매니페스트에 있는데 목록에서 빠져 '권한 전체'라는 머리말과 맞지 않았다(QA 2차)
        Item(
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Copy.text(R.string.about_perm_overlay_title),
            Copy.text(R.string.about_perm_overlay_why),
            How.USER,
            optional = true
        ),
        // 권한은 아니지만 사용자가 알아야 할 시스템 접근이라 함께 보인다(1.17.5 전수 점검: '전체 권한'에서 빠져 있었다)
        Item(
            ACCESSIBILITY,
            Copy.text(R.string.about_perm_accessibility_title),
            Copy.text(R.string.about_perm_accessibility_why),
            How.GOAT,
            optional = true
        ),
        Item(
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Copy.text(R.string.about_perm_boot_title),
            Copy.text(R.string.about_perm_boot_why),
            How.INSTALL
        ),
        Item(
            Manifest.permission.FOREGROUND_SERVICE,
            Copy.text(R.string.about_perm_foreground_title),
            Copy.text(R.string.about_perm_foreground_why),
            How.INSTALL
        ),
        Item(
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
            Copy.text(R.string.about_perm_foreground_special_title),
            Copy.text(R.string.about_perm_foreground_special_why),
            How.INSTALL
        ),
        Item(
            Manifest.permission.VIBRATE,
            Copy.text(R.string.about_perm_vibrate_title),
            Copy.text(R.string.about_perm_vibrate_why),
            How.INSTALL
        ),
        Item(
            Manifest.permission.INTERNET,
            Copy.text(R.string.about_perm_internet_title),
            Copy.text(R.string.about_perm_internet_why),
            How.INSTALL
        ),
        Item(
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            Copy.text(R.string.about_perm_install_title),
            Copy.text(R.string.about_perm_install_why),
            How.USER,
            optional = true
        ),
        Item(
            Manifest.permission.WAKE_LOCK,
            Copy.text(R.string.about_perm_wake_lock_title),
            Copy.text(R.string.about_perm_wake_lock_why),
            How.INSTALL
        )
    )

    fun granted(context: Context, item: Item): Boolean = when {
        !item.applies -> true
        // 사용 기록 접근은 권한이 아니라 앱 작업(AppOps)으로 허용 여부가 정해진다
        item.permission == Manifest.permission.PACKAGE_USAGE_STATS -> AppUsage.hasAccess(context)
        item.permission == Manifest.permission.REQUEST_INSTALL_PACKAGES -> context.packageManager.canRequestPackageInstalls()
        item.permission == Manifest.permission.SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(context)
        item.permission == ACCESSIBILITY -> VolumeRotate.enabled(context)
        else -> context.checkSelfPermission(item.permission) == PackageManager.PERMISSION_GRANTED
    }
}

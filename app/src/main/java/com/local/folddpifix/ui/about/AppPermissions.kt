package com.local.folddpifix.ui.about

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** 앱이 쓰는 권한과 받는 방법. 최종 APK의 권한 목록(aapt2 dump permissions)과 맞춘다. */
internal object AppPermissions {

    enum class How(val label: String) {
        ADB("Shizuku·PC adb로 1회 부여"),
        USER("사용자 허용"),
        INSTALL("설치 시 자동"),
    }

    data class Item(
        val permission: String,
        val title: String,
        val why: String,
        val how: How,
        /** 이 기기에서 쓰이지 않는 권한(예: Android 12의 알림 권한)이면 false. */
        val applies: Boolean = true,
    )

    val all: List<Item> = listOf(
        Item(
            Manifest.permission.WRITE_SECURE_SETTINGS, "시스템 설정 변경",
            "외부·내부 화면의 DPI를 바꾸는 데 필요합니다. 일반 권한 창으로는 받을 수 없어 Shizuku나 PC의 adb로 받습니다.", How.ADB,
        ),
        Item(
            Manifest.permission.POST_NOTIFICATIONS, "알림",
            "외부에서 DPI가 바뀌었을 때 선택 알림과 접기/펼치기 감시 알림을 보여 줍니다.", How.USER,
            applies = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        ),
        Item(
            Manifest.permission.RECEIVE_BOOT_COMPLETED, "부팅 완료 알림 받기",
            "재부팅 뒤 설정한 DPI를 다시 적용합니다.", How.INSTALL,
        ),
        Item(
            Manifest.permission.FOREGROUND_SERVICE, "포그라운드 서비스",
            "접고 펼칠 때 화면 전환을 감지해 DPI를 다시 맞춥니다.", How.INSTALL,
        ),
        Item(
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE, "포그라운드 서비스(특수 용도)",
            "위 감시 서비스의 종류(화면 전환 시 DPI 재적용)를 시스템에 알립니다.", How.INSTALL,
        ),
        Item(
            Manifest.permission.WAKE_LOCK, "절전 모드 해제 방지",
            "백그라운드 작업(WorkManager)이 DPI 확인을 끝낼 때까지 잠깐 깨어 있게 합니다.", How.INSTALL,
        ),
    )

    fun granted(context: Context, item: Item): Boolean =
        !item.applies || context.checkSelfPermission(item.permission) == PackageManager.PERMISSION_GRANTED
}

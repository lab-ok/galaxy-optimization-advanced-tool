package com.labok.goat.ui.about

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.labok.goat.data.appsize.AppUsage

/** 앱이 쓰는 권한과 받는 방법. 최종 APK의 권한 목록(aapt2 dump permissions)과 맞춘다. */
internal object AppPermissions {

    enum class How(val label: String) {
        ADB("Shizuku·PC adb로 1회 부여"),
        USER("사용자 허용"),
        INSTALL("설치 시 자동")
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

    // 권한 이름은 컴파일 때 문자열로 박히는 상수라 낮은 버전에서도 안전하다. 해당 버전 미만은 applies로 거른다.

    @SuppressLint("InlinedApi")
    val all: List<Item> = listOf(
        Item(
            Manifest.permission.WRITE_SECURE_SETTINGS,
            "시스템 설정 변경",
            "외부·내부 화면의 DPI를 바꾸고, 흔들어서 화면 회전에서 회전 잠금 방향을 바꾸는 데 필요합니다. 일반 권한 창으로는 받을 수 없어 Shizuku나 PC의 adb로 받습니다.",
            How.ADB
        ),
        Item(
            Manifest.permission.POST_NOTIFICATIONS,
            "알림",
            "외부 DPI 변경 선택 알림, 접기/펼치기 감시 알림, 앱별 크기 조절 알림, 흔들어서 화면 회전 알림을 보여 줍니다.",
            How.USER,
            applies = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ),
        Item(
            SHIZUKU_API,
            "Shizuku 사용",
            "PC 없이 시스템 설정 권한을 받고, 앱별 화면 배율을 바꿀 때 Shizuku에 셸 권한을 빌립니다. Shizuku 창에서 허용합니다.",
            How.USER
        ),
        Item(
            Manifest.permission.PACKAGE_USAGE_STATS,
            "사용 기록 접근",
            "앱별 크기 목록을 최근 사용 순으로 정렬합니다. Shizuku가 연결되면 GOAT가 스스로 허용합니다.",
            How.USER
        ),
        // 매니페스트에 있는데 목록에서 빠져 '권한 전체'라는 머리말과 맞지 않았다(QA 2차)
        Item(
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            "다른 앱 위에 표시",
            "상태 표시줄의 상단바 숨기기(실험)가 다른 앱 위에 투명한 창을 띄워 상단바를 숨기는 데 씁니다. Shizuku가 연결되면 GOAT가 스스로 허용합니다.",
            How.USER,
            optional = true
        ),
        Item(
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            "부팅 완료 알림 받기",
            "재부팅 뒤 설정한 DPI를 다시 적용합니다.",
            How.INSTALL
        ),
        Item(
            Manifest.permission.FOREGROUND_SERVICE,
            "포그라운드 서비스",
            "접고 펼칠 때 DPI를 다시 맞추고, 켜 두면 알림창 앱별 크기 조절과 흔들기 감지를 계속 실행합니다.",
            How.INSTALL
        ),
        Item(
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
            "포그라운드 서비스(특수 용도)",
            "위 서비스의 종류(화면 전환 시 DPI 재적용, 알림창 크기 조절, 흔들기 감지)를 시스템에 알립니다.",
            How.INSTALL
        ),
        Item(
            Manifest.permission.VIBRATE,
            "진동",
            "흔들어서 화면 회전에서 화면을 돌렸을 때 짧게 진동으로 알려 줍니다.",
            How.INSTALL
        ),
        Item(
            Manifest.permission.INTERNET,
            "인터넷",
            "업데이트 확인에만 씁니다. GitHub 릴리스에서 새 버전을 확인하고 APK를 내려받으며, 그 밖의 데이터는 보내지 않습니다.",
            How.INSTALL
        ),
        Item(
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            "앱 설치",
            "Shizuku가 없을 때 새 버전을 시스템 설치 창으로 설치합니다. 처음 한 번 'GOAT의 앱 설치 허용'을 켜야 합니다.",
            How.USER,
            optional = true
        ),
        Item(
            Manifest.permission.WAKE_LOCK,
            "절전 모드 해제 방지",
            "백그라운드 작업(WorkManager)이 DPI 확인을 끝낼 때까지 잠깐 깨어 있게 합니다.",
            How.INSTALL
        )
    )

    fun granted(context: Context, item: Item): Boolean = when {
        !item.applies -> true
        // 사용 기록 접근은 권한이 아니라 앱 작업(AppOps)으로 허용 여부가 정해진다
        item.permission == Manifest.permission.PACKAGE_USAGE_STATS -> AppUsage.hasAccess(context)
        item.permission == Manifest.permission.REQUEST_INSTALL_PACKAGES -> context.packageManager.canRequestPackageInstalls()
        item.permission == Manifest.permission.SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(context)
        else -> context.checkSelfPermission(item.permission) == PackageManager.PERMISSION_GRANTED
    }
}

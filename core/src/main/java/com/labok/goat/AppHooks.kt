package com.labok.goat

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * 기능 모듈이 앱(조립하는 쪽)에 부탁하는 일. 기능 모듈은 앱이나 다른 기능을 직접 부르지 못하므로,
 * 앱이 시작할 때(App.onCreate) 여기에 실제 동작을 넣어 둔다. 비어 있으면 아무것도 하지 않는다.
 */
object AppHooks {
    /** 알림을 누르면 열 첫 화면. */
    @Volatile var mainActivity: Class<out Activity>? = null

    /** 첫 화면을 여는 인텐트(알림·바로 가기용). */
    fun mainIntent(context: Context): Intent = mainActivity?.let { Intent(context, it) }
        ?: context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()

    /** 상주 서비스가 필요한지 다시 맞춘다(GoatService.sync). */
    @Volatile var syncService: (Context) -> Unit = {}

    /** 볼륨 키가 눌린 것을 사용 통계에 센다. */
    @Volatile var countVolume: (Context) -> Unit = {}

    /** 앱별 배율 알림에 덧붙일 다른 기능 상태(흔들어서 회전·플렉스가 켜져 있는지). */
    @Volatile var densityNoteExtras: (Context) -> String? = { null }

    /** 문제 신고용 진단 기록 전체(DiagnosticReport.build). 시험 화면이 결과에 덧붙인다. */
    @Volatile var diagnosticReport: (Context) -> String = { "" }

    /** Shizuku 끊김 알림을 지금 상태에 맞게 다시 띄우거나 지운다(ShizukuNotice.update). */
    @Volatile var shizukuNoticeUpdate: (Context) -> Unit = {}

    /** 앱별 배율을 바꿔 둔 앱 수(해상도 낮추기가 경고에 쓴다, DensityShell.savedCount). */
    @Volatile var savedAppDensityCount: (Context) -> Int = { 0 }

    /** 기능을 이 기기에서 쓸 수 있는지 사이드바에 알린다(FeatureSupport.note). [feature]는 사이드바 항목 이름. */
    @Volatile var featureSupport: (Context, String, Boolean) -> Unit = { _, _, _ -> }
}

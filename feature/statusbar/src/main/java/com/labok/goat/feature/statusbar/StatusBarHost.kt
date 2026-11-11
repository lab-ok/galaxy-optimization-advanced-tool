package com.labok.goat.feature.statusbar

import android.content.Context
import android.graphics.Bitmap

/**
 * 상단바 숨기기 모듈이 앱 공통 기능을 쓰는 통로. 모듈은 앱 코드를 직접 부르지 않고, 앱이 시작할 때 [host]를 넣어 준다(App.onCreate).
 * 공통 코드(Shizuku 셸, GOAT 기록, 알림 아이콘)는 앱에 그대로 두고 이 네 가지만 빌려 쓴다.
 */
object StatusBarHost {
    interface Host {
        /** Shizuku가 연결돼 셸을 쓸 수 있는지. */
        fun shizukuReady(): Boolean

        /** Shizuku 셸로 실행한 표준 출력. 쓸 수 없으면 null. */
        fun shell(command: String): String?

        /** GOAT 기록(진단 파일의 「앱 로그」)에 한 줄 남긴다. */
        fun note(context: Context, text: String)

        /** 알림 작은 아이콘(drawable)과 큰 아이콘. */
        val smallIcon: Int
        fun largeIcon(context: Context): Bitmap?
    }

    @Volatile lateinit var host: Host

    fun shizukuReady() = host.shizukuReady()
    fun shell(command: String) = host.shell(command)
    fun note(context: Context, text: String) = host.note(context, text)
}

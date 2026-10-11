package com.labok.goat.data.rotate

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 흔들어서 화면 회전을 알림 없이 돌리는 방식. 접근성 서비스는 시스템이 직접 띄워 두어 알림 없이도 화면이 켜져 있는 동안
 * 센서를 쓸 수 있다. 화면 내용은 읽지 않고(설정 파일에서 이벤트·창 내용 읽기를 모두 끔), 흔들기만 감지한다.
 * GOAT가 시스템 설정 권한으로 접근성 목록에 직접 넣고 뺀다([ShakeRotate.start]).
 */
class ShakeAccessibilityService : AccessibilityService() {
    private var engine: ShakeEngine? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = ShakeEngine(this).also { it.start() }
        running = engine
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        engine?.stop()
        if (running === engine) running = null
        engine = null
        super.onDestroy()
    }

    companion object {
        /** 지금 돌고 있는 엔진(민감도를 바꾸면 다시 읽게 한다). */
        @Volatile
        var running: ShakeEngine? = null
            internal set
    }
}

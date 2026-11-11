package com.labok.goat.data.lab

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.labok.goat.data.log.LogRepository

/**
 * 상단바 숨기기 입력 도우미(접근성 서비스). 숨기기 창이 포커스를 쥔 동안 다른 앱의 입력란이 눌리면 포커스를 바로 넘기고
 * 그 입력란을 다시 눌러 첫 누름에 키보드가 뜨게 한다. 입력란인지만 보고 화면 글자는 읽지 않는다.
 * 1.27.0에서 상단바 제스처 서비스가 하던 일인데, 1.49.0에서 그 서비스를 지우며 함께 빠져 두세 번 눌러야 했다
 * (11-11 소장님 실기). Android 17은 입력 기록(dumpsys input)에서 누른 좌표를 지워 좌표로 다시 누를 수도 없다(1.55.2 실측).
 */
class StatusBarInputService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED && event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return
        if (!StatusBarHider.on.value || event.packageName == packageName) return
        val node = event.source ?: return
        if (!node.isEditable) return
        StatusBarHider.yieldForInput(this) {
            node.refresh()
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        fun component(context: Context) = ComponentName(context, StatusBarInputService::class.java)

        /** 접근성 설정에 켜져 있는지. */
        fun enabled(context: Context): Boolean {
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return list.split(':').any { ComponentName.unflattenFromString(it) == component(context) }
        }

        /** 시스템 설정 권한으로 켜거나 끈다. 성공하면 true. */
        fun setEnabled(context: Context, on: Boolean): Boolean = runCatching {
            val r = context.contentResolver
            val c = component(context)
            val others = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != c }
            Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (if (on) others + c.flattenToString() else others).joinToString(":"))
            if (on) Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            enabled(context) == on
        }.getOrDefault(false).also { ok ->
            LogRepository.from(context).add("상단바 숨기기: 입력 도우미 ${if (on) "켬" else "끔"}${if (ok) "" else " 실패"}")
        }
    }
}

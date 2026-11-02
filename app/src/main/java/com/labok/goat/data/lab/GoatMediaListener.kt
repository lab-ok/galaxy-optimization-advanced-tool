package com.labok.goat.data.lab

import android.service.notification.NotificationListenerService

/**
 * 미디어 세션 목록을 공식 방법(MediaSessionManager.getActiveSessions)으로 받기 위한 빈 알림 접근 서비스(GOAT 플렉스 패널용).
 * 알림 내용은 읽지 않는다. 셸 이름으로 세션을 받는 숨은 함수가 기기마다 달라(안드로이드 17 실기에서 실패) 이 길을 둘째로 둔다.
 */
class GoatMediaListener : NotificationListenerService()

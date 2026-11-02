package com.labok.goat.data.lab

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.UserHandle
import com.labok.goat.data.HiddenApi
import com.labok.goat.data.UserId
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 다른 앱의 미디어 세션 조작(GOAT 플렉스 패널용). 앱 목록의 세션 토큰은 미디어 조작 권한이 있어야 받는데, Shizuku 셸에는 그 권한이 있어
 * 세션 관리자에게 셸 이름으로 토큰을 받고, 토큰으로 만든 MediaController로는 GOAT가 직접 재생·이동·속도를 바꾼다.
 * 세션이 없는 앱이면 null. 작업 스레드에서 부른다.
 */
object MediaRemote {
    /** [pkg] 앱의 세션(없으면 지금 재생 중인 아무 세션). */
    fun controller(context: Context, pkg: String?): MediaController? = runCatching {
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return null
        val sm = HiddenApi.asInterface("android.media.session.ISessionManager", ShizukuBinderWrapper(SystemServiceHelper.getSystemService("media_session")))
        val m = sm.javaClass.methods.first { it.name == "getSessions" && it.parameterTypes.size == 2 }
        val user: Any = if (m.parameterTypes[1] == Int::class.javaPrimitiveType) UserId.current() else UserHandle.getUserHandleForUid(android.os.Process.myUid())

        @Suppress("UNCHECKED_CAST")
        val tokens = (m.invoke(sm, null, user) as? List<MediaSession.Token>).orEmpty()
        val all = tokens.map { MediaController(context, it) }
        all.firstOrNull { it.packageName == pkg } ?: all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    }.getOrNull()

    /** 진단용: 세션 목록 한 줄씩(패키지·상태·제목). */
    fun describe(context: Context): String = runCatching {
        val sm = HiddenApi.asInterface("android.media.session.ISessionManager", ShizukuBinderWrapper(SystemServiceHelper.getSystemService("media_session")))
        val m = sm.javaClass.methods.first { it.name == "getSessions" && it.parameterTypes.size == 2 }
        val user: Any = if (m.parameterTypes[1] == Int::class.javaPrimitiveType) UserId.current() else UserHandle.getUserHandleForUid(android.os.Process.myUid())

        @Suppress("UNCHECKED_CAST")
        val tokens = (m.invoke(sm, null, user) as? List<MediaSession.Token>).orEmpty()
        tokens.map { MediaController(context, it) }.joinToString(" | ") { c ->
            val md = c.metadata
            "${c.packageName} 상태 ${c.playbackState?.state} 위치 ${c.playbackState?.position} 길이 ${md?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)} 제목 ${md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)} 동작 0x${java.lang.Long.toHexString(c.playbackState?.actions ?: 0)}"
        }.ifEmpty { "세션 없음" }
    }.getOrElse { "세션 읽기 실패: ${it.javaClass.simpleName} ${it.message}" }
}

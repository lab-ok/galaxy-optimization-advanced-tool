package com.labok.goat.data.lab

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.UserHandle
import com.labok.goat.data.HiddenApi
import com.labok.goat.data.UserId
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 다른 앱의 미디어 세션 조작(GOAT 플렉스 패널용). 세션 토큰만 받으면 MediaController로 GOAT가 직접 재생·이동·속도를 바꾼다.
 * 토큰 받는 길은 둘이다.
 * 1. Shizuku 셸 이름으로 세션 관리자의 세션 목록 함수를 부른다. 함수 모양이 기기·버전마다 달라(안드로이드 17 실기에서 인자 둘짜리가 없어 실패)
 *    이름에 Sessions가 들어간 목록 함수를 모두 찾아 인자 종류에 맞춰 불러 본다.
 * 2. 안 되면 GOAT의 빈 알림 접근 서비스([GoatMediaListener])를 Shizuku로 허용하고 공식 getActiveSessions로 받는다.
 * 세션이 없는 앱이면 null. 작업 스레드에서 부른다.
 */
object MediaRemote {
    @Volatile private var lastWay = ""

    /** 마지막으로 세션을 받은 길(진단용). */
    val way get() = lastWay

    /** [pkg] 앱의 세션(없으면 지금 재생 중인 아무 세션). */
    fun controller(context: Context, pkg: String?): MediaController? {
        val all = controllers(context)
        return all.firstOrNull { it.packageName == pkg } ?: all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    }

    private fun controllers(context: Context): List<MediaController> {
        if (ShizukuAccess.status.value != ShizukuAccess.State.READY) return emptyList()
        val viaShell = runCatching { shellTokens() }.onFailure { lastWay = "셸 실패(${it.javaClass.simpleName})" }.getOrNull()
        // 셸 길이 오류 없이 빈 목록을 주는 기기가 있다(11-02 폴드8 안드로이드 17: 넷플릭스 재생 중인데 '세션 없음'). 비었으면 알림 접근 길도 본다
        if (!viaShell.isNullOrEmpty()) {
            lastWay = "셸"
            return viaShell.map { MediaController(context, it) }
        }
        if (viaShell != null) lastWay = "셸 빈 목록"
        return runCatching {
            val me = ComponentName(context, GoatMediaListener::class.java)
            val msm = context.getSystemService(MediaSessionManager::class.java)
            val list = runCatching { msm.getActiveSessions(me) }.getOrElse {
                ShizukuAccess.shell("cmd notification allow_listener ${me.flattenToString()}")
                LogRepository.from(context).add("GOAT 플렉스 패널: 재생 정보를 받으려고 GOAT 알림 접근(빈 서비스)을 켬")
                Thread.sleep(300)
                msm.getActiveSessions(me)
            }
            lastWay += " → 알림 접근"
            list
        }.getOrElse {
            lastWay += " → 알림 접근 실패(${it.javaClass.simpleName})"
            emptyList()
        }
    }

    /** 셸 이름으로 세션 관리자의 목록 함수를 찾아 부른다. 맞는 함수가 없거나 모두 실패하면 예외. */
    private fun shellTokens(): List<MediaSession.Token> {
        val sm = HiddenApi.asInterface("android.media.session.ISessionManager", ShizukuBinderWrapper(SystemServiceHelper.getSystemService("media_session")))
        val candidates = sm.javaClass.methods.filter { it.name.startsWith("getSessions") && List::class.java.isAssignableFrom(it.returnType) }
        var last: Throwable? = null
        for (m in candidates) {
            val args = m.parameterTypes.map { t ->
                when (t) {
                    Int::class.javaPrimitiveType -> UserId.current()
                    UserHandle::class.java -> UserHandle.getUserHandleForUid(android.os.Process.myUid())
                    String::class.java -> "com.android.shell"
                    else -> null
                }
            }
            val r = runCatching { m.invoke(sm, *args.toTypedArray()) }.onFailure { last = it }.getOrNull() ?: continue
            @Suppress("UNCHECKED_CAST")
            return (r as List<Any?>).filterIsInstance<MediaSession.Token>()
        }
        throw last ?: NoSuchMethodException("getSessions: " + sm.javaClass.methods.filter { "ession" in it.name }.joinToString { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }.take(400))
    }

    /** 진단용: 세션을 받은 길과 세션 목록 한 줄씩(패키지·상태·제목·지원 동작). */
    fun describe(context: Context): String {
        val all = controllers(context)
        return "[$lastWay] " + all.joinToString(" | ") { c ->
            val md = c.metadata
            "${c.packageName} 상태 ${c.playbackState?.state} 위치 ${c.playbackState?.position} 길이 ${md?.getLong(MediaMetadata.METADATA_KEY_DURATION)} 제목 ${md?.getString(MediaMetadata.METADATA_KEY_TITLE)} 동작 0x${java.lang.Long.toHexString(c.playbackState?.actions ?: 0)} 포스터 ${md?.getBitmap(MediaMetadata.METADATA_KEY_ART) != null || md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null}"
        }.ifEmpty { "세션 없음" }
    }
}

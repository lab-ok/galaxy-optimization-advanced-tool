package com.labok.goat.data.tweaks

import android.content.Context
import android.provider.Settings
import com.labok.goat.data.log.LogRepository

/**
 * 애니메이션 속도: 개발자 옵션의 창·전환·애니메이터 배율(전역 설정)을 바꾼다. 값이 작을수록 빠르고(0이면 끔), 1이 기본이다.
 * 전역 설정이라 고급 화면 배율 설정과 같은 시스템 설정 권한(WRITE_SECURE_SETTINGS)으로 바꾼다. 재부팅해도 유지된다.
 * 개발자 옵션을 끄면 기기가 세 값을 기본(1배)으로 되돌린다. GOAT로 고른 값을 기억해 두었다가, 세 값이 모두 기본으로 풀리면
 * [AnimationKeepWorker]가 고른 값으로 되돌린다([keep], 끌 수 있음). 끔(0)처럼 기본이 아닌 값으로 바뀐 것은 사용자나 접근성
 * 설정(애니메이션 제거)이 고른 것으로 보고 따른다.
 */
object AnimationSpeed {
    /** 바꾸는 세 설정과 화면 이름. */
    enum class Kind(val key: String, val label: String, val short: String) {
        WINDOW(Settings.Global.WINDOW_ANIMATION_SCALE, "창 애니메이션", "창"),
        TRANSITION(Settings.Global.TRANSITION_ANIMATION_SCALE, "화면 전환 애니메이션", "전환"),
        ANIMATOR(Settings.Global.ANIMATOR_DURATION_SCALE, "애니메이터 길이", "애니메이터")
    }

    fun read(context: Context, kind: Kind): Float = runCatching { Settings.Global.getFloat(context.contentResolver, kind.key, 1f) }.getOrDefault(1f)

    /** 하나를 바꾼다. 성공하면 true. */
    fun set(context: Context, kind: Kind, value: Float, how: String): Boolean {
        val before = read(context, kind)
        val ok = runCatching { Settings.Global.putFloat(context.contentResolver, kind.key, value) }.getOrDefault(false)
        LogRepository.from(context).add("애니메이션 속도($how): ${kind.label} ${label(before)} → ${label(value)}${if (ok) "" else " 실패"}")
        if (ok) {
            prefs(context).edit().putFloat(kind.key, value).apply()
            AnimationKeepWorker.sync(context)
        }
        return ok
    }

    /** GOAT로 마지막에 고른 값(없으면 null). */
    fun chosen(context: Context, kind: Kind): Float? = prefs(context).takeIf { it.contains(kind.key) }?.getFloat(kind.key, 1f)

    /** 기본으로 풀리면 되돌리기(기본 켬). */
    fun keep(context: Context) = prefs(context).getBoolean(KEY_KEEP, true)

    fun setKeep(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP, on).apply()
        AnimationKeepWorker.sync(context)
    }

    /** 되돌릴 일이 있는지: 유지가 켜져 있고, 고른 값 가운데 기본이 아닌 것이 있을 때. */
    fun keepNeeded(context: Context) = keep(context) && Kind.entries.any { chosen(context, it)?.let { v -> v != 1f } == true }

    /**
     * 세 값이 모두 기본(1배)으로 풀렸으면 고른 값으로 되돌린다. 되돌렸으면 true. 작업 스레드에서 부른다.
     * 고른 값과 지금 값이 이미 같거나, 하나라도 기본이 아닌 값이면(사용자·접근성이 바꾼 것) 그대로 둔다.
     */
    fun restoreIfReset(context: Context): Boolean {
        if (!keepNeeded(context)) return false
        if (Kind.entries.any { read(context, it) != 1f }) return false
        var any = false
        Kind.entries.forEach { kind ->
            val v = chosen(context, kind) ?: return@forEach
            if (v != 1f && runCatching { Settings.Global.putFloat(context.contentResolver, kind.key, v) }.getOrDefault(false)) any = true
        }
        if (any) LogRepository.from(context).add("애니메이션 속도: 기본값으로 풀려 고른 값으로 되돌림(${Kind.entries.joinToString(" · ") { "${it.short} ${label(read(context, it))}" }})")
        return any
    }

    /**
     * 유지 기능이 생기기 전(1.15.3 이하)에 고른 값은 기억돼 있지 않다. 기억한 값이 하나도 없고 지금 값이 기본이 아니면
     * 지금 값을 고른 값으로 삼는다(그래야 다음에 풀렸을 때 되돌릴 수 있다).
     */
    fun seedIfMissing(context: Context) {
        if (Kind.entries.any { chosen(context, it) != null }) return
        val now = Kind.entries.associateWith { read(context, it) }
        if (now.values.all { it == 1f }) return
        prefs(context).edit().apply { now.forEach { (k, v) -> putFloat(k.key, v) } }.apply()
    }

    private const val KEY_KEEP = "keep"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("animation_speed", Context.MODE_PRIVATE)

    /** 셋을 같은 값으로. */
    fun setAll(context: Context, value: Float, how: String): Boolean = Kind.entries.map { set(context, it, value, how) }.all { it }

    fun label(v: Float) = when {
        v == 0f -> "끔"
        v == 1f -> "기본"
        else -> "${"%.2f".format(v).trimEnd('0').trimEnd('.')}배"
    }
}

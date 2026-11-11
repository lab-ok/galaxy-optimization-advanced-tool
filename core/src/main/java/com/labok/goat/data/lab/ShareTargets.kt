package com.labok.goat.data.lab

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 공유 창에서 GOAT 자신(TXT로 공유·전사문 저장·클립보드 공유)을 받는 앱 목록에서 뺀다.
 * GOAT가 보낸 것이 다시 GOAT로 돌아오는 고리를 막는다(권한 설정 안내·문제 신고·실험 결과 공유도 같이 쓴다, 1.51.0 QA).
 */
fun Intent.withoutGoat(context: Context): Intent = putExtra(
    Intent.EXTRA_EXCLUDE_COMPONENTS,
    arrayOf(
        ComponentName(context, "com.labok.goat.ui.lab.MemoShareActivity"),
        ComponentName(context, "com.labok.goat.ui.lab.TranscriptShareActivity"),
        ComponentName(context, "com.labok.goat.ui.lab.ClipShareActivity")
    )
)

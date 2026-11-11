package com.labok.goat.data.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/** 기기 보호 저장소(잠금 해제 전에도 열림)의 설정·기록 파일 위치. 공통 모듈에서 기록·크래시 파일이 쓴다. */
object Storage {
    private fun dpContext(context: Context): Context = context.applicationContext.createDeviceProtectedStorageContext()

    fun prefs(context: Context): SharedPreferences = dpContext(context).getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun logFile(context: Context) = File(dpContext(context).filesDir, "dpi_log.txt")

    fun crashFile(context: Context) = File(dpContext(context).filesDir, "crash_log.txt")

    /** 잠금 해제 전에 쌓아 둔, 공용 로그 파일로 옮길 내용. */
    fun pendingPublicLog(context: Context) = File(dpContext(context).filesDir, "pending_public.log")
}

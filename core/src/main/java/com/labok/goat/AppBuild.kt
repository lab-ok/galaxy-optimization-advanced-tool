package com.labok.goat

/**
 * 앱 빌드 정보. 기능·공통 모듈은 앱의 BuildConfig를 볼 수 없어, 앱이 시작할 때(App.attachBaseContext, 다른 구성요소보다 먼저) 채운다.
 */
object AppBuild {
    const val APPLICATION_ID = "com.labok.goat"

    @Volatile var versionName: String = ""
    @Volatile var versionCode: Int = 0

    /** 개발자용 실험실 빌드인지(lab 빌드 종류). */
    @Volatile var lab: Boolean = false
}

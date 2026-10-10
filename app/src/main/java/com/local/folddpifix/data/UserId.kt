package com.local.folddpifix.data

import android.os.Process

/** 지금 사용자(멀티 유저)의 ID. 앱 uid는 '사용자 ID × 100000 + 앱 ID'로 정해진다. */
object UserId {
    private const val PER_USER_RANGE = 100_000

    fun current(): Int = Process.myUid() / PER_USER_RANGE
}

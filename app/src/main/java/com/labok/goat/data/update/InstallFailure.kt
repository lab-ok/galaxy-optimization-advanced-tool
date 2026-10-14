package com.labok.goat.data.update

import android.content.pm.PackageInstaller

/**
 * 설치 실패를 사용자가 알아듣는 한국어로 푼다. 시스템 설치 창의 결과 코드([PackageInstaller] STATUS_*)와
 * 설치 실패 문구(INSTALL_FAILED_* 등, Shizuku `pm install`의 "Failure [...]" 포함)를 모두 받는다.
 * 모르는 코드는 원문을 괄호로 붙여 신고할 때 단서로 남긴다.
 */
object InstallFailure {
    fun explain(status: Int?, message: String?): String {
        val m = message.orEmpty()
        fun has(code: String) = m.contains(code, ignoreCase = true)
        return when {
            has("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || has("INCONSISTENT_CERTIFICATES") || has("signatures do not match") ->
                "지금 설치된 GOAT와 서명이 달라 덮어 설치할 수 없습니다. GitHub가 아닌 곳에서 받은 GOAT라면 지웠다가 다시 설치해 주세요(GOAT 설정은 처음부터 다시 해야 합니다)."
            has("INSUFFICIENT_STORAGE") || status == PackageInstaller.STATUS_FAILURE_STORAGE ->
                "저장 공간이 부족합니다. 공간을 비운 뒤 다시 시도해 주세요."
            has("VERSION_DOWNGRADE") ->
                "지금 설치된 GOAT가 더 새 버전이라 설치하지 않았습니다."
            status == PackageInstaller.STATUS_FAILURE_ABORTED || has("aborted") || has("cancel") ->
                "설치를 취소했습니다. 다시 시도하면 설치 창이 다시 열립니다."
            status == PackageInstaller.STATUS_FAILURE_BLOCKED || has("blocked") || has("VERIFICATION_FAILURE") ->
                "기기 보안 기능(플레이 프로텍트·삼성 자동 차단)이 설치를 막았습니다. 해당 기능을 잠시 끄거나 GitHub에서 APK를 받아 직접 설치해 주세요."
            status == PackageInstaller.STATUS_FAILURE_INVALID || has("INVALID_APK") || has("PARSE_FAILED") ->
                "내려받은 파일이 손상됐습니다. 다시 시도해 주세요."
            status == PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "같은 이름의 다른 앱과 충돌해 설치하지 못했습니다."
            status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                "이 기기와 맞지 않아 설치하지 못했습니다."
            else -> "설치하지 못했습니다." + if (m.isNotBlank()) " (${m.take(80)})" else ""
        }
    }
}

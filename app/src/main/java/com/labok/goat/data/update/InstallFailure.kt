package com.labok.goat.data.update

import android.content.pm.PackageInstaller
import com.labok.goat.R
import com.labok.goat.ui.text.Copy

/**
 * 설치 실패를 사용자가 알아듣는 한국어로 푼다. 시스템 설치 창의 결과 코드([PackageInstaller] STATUS_*)와
 * 설치 실패 문구(INSTALL_FAILED_* 등, Shizuku `pm install`의 "Failure [...]" 포함)를 모두 받는다.
 * 모르는 코드는 원문을 괄호로 붙여 신고할 때 단서로 남긴다.
 */
object InstallFailure {
    /** 'GOAT: 출처를 알 수 없는 앱 설치'가 꺼져 있을 때(시스템이 "Self update is blocked by unknown source package"로 막는다). */
    val UNKNOWN_SOURCE: String get() = Copy.text(R.string.update_fail_unknown_source)

    fun explain(status: Int?, message: String?): String {
        val m = message.orEmpty()
        fun has(code: String) = m.contains(code, ignoreCase = true)
        return when {
            has("unknown source") || has("User rejected permissions") -> UNKNOWN_SOURCE
            has("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || has("INCONSISTENT_CERTIFICATES") || has("signatures do not match") ->
                Copy.text(R.string.update_fail_signature)
            has("INSUFFICIENT_STORAGE") || status == PackageInstaller.STATUS_FAILURE_STORAGE ->
                Copy.text(R.string.update_fail_storage)
            has("VERSION_DOWNGRADE") ->
                Copy.text(R.string.update_fail_downgrade)
            status == PackageInstaller.STATUS_FAILURE_ABORTED || has("aborted") || has("cancel") ->
                Copy.text(R.string.update_fail_aborted)
            status == PackageInstaller.STATUS_FAILURE_BLOCKED || has("blocked") || has("VERIFICATION_FAILURE") ->
                Copy.text(R.string.update_fail_blocked)
            status == PackageInstaller.STATUS_FAILURE_INVALID || has("INVALID_APK") || has("PARSE_FAILED") ->
                Copy.text(R.string.update_fail_invalid)
            status == PackageInstaller.STATUS_FAILURE_CONFLICT ->
                Copy.text(R.string.update_fail_conflict)
            status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Copy.text(R.string.update_fail_incompatible)
            else -> Copy.text(R.string.update_fail_generic) + if (m.isNotBlank()) " (${m.take(80)})" else ""
        }
    }
}

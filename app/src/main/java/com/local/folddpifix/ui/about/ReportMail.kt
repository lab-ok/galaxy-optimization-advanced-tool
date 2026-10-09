package com.local.folddpifix.ui.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.local.folddpifix.AppInfo

/**
 * 개발자에게 보내는 메일.
 * - 문의([inquiry]): 첨부 없는 메일 작성 창.
 * - 문제 신고([send]): 받는 사람·제목·본문 양식을 채우고 진단 파일과 오늘 로그를 첨부해 공유 창으로 보낸다.
 *   (첨부가 있는 메일은 실기기에서 메일 앱으로 바로 열리지 않고 공유 창으로 넘어가므로, 처음부터 공유 창을 쓴다)
 */
internal object ReportMail {

    /** 앱 정보의 문의 메일: 첨부 없이 받는 사람·제목만 채운 메일 작성 창(mailto). */
    fun inquiry(context: Context) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${DeveloperProfile.CONTACT_EMAIL}"))
            .putExtra(Intent.EXTRA_SUBJECT, "[${AppInfo.NAME} $version] 문의")
        try {
            context.startActivity(mail)
        } catch (e: ActivityNotFoundException) {
            // 메일 앱이 없으면 아무것도 하지 않는다.
        }
    }

    enum class Kind(val label: String) { INQUIRY("문의"), REPORT("문제 신고") }

    fun send(context: Context, kind: Kind, attachments: List<Uri>) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
        val body = buildString {
            appendLine(if (kind == Kind.REPORT) "어떤 문제가 있었나요? (언제, 무엇을 했을 때, 어떻게 됐는지)" else "문의 내용을 적어 주세요.")
            appendLine()
            appendLine()
            appendLine("---")
            if (attachments.isNotEmpty()) appendLine("진단 파일과 오늘 로그가 첨부되어 있습니다.")
            appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${AppInfo.NAME} $version")
        }
        val mail = (if (attachments.size <= 1) Intent(Intent.ACTION_SEND) else Intent(Intent.ACTION_SEND_MULTIPLE)).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(DeveloperProfile.CONTACT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "[${AppInfo.NAME} $version] ${kind.label}")
            putExtra(Intent.EXTRA_TEXT, body)
            when (attachments.size) {
                0 -> Unit
                1 -> putExtra(Intent.EXTRA_STREAM, attachments[0])
                else -> putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachments))
            }
            if (attachments.isNotEmpty()) {
                clipData = ClipData.newRawUri(null, attachments[0]).also { cd -> attachments.drop(1).forEach { cd.addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(mail, kind.label))
    }
}

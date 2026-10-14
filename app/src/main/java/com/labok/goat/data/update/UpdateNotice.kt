package com.labok.goat.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.labok.goat.BuildConfig
import com.labok.goat.R
import com.labok.goat.ui.MainActivity

/**
 * 업데이트를 마친 뒤 알림: 안드로이드는 업데이트된 앱이 스스로 화면을 열지 못하게 막으므로, GOAT가 시작한 업데이트가
 * 끝나면(MY_PACKAGE_REPLACED) "업데이트됨 · 눌러서 열기" 알림을 띄운다. GOAT를 열면 알림을 지운다.
 * 사용자가 직접 설치한 경우에는 띄우지 않도록, GOAT가 설치를 시작할 때 [expect]로 표시해 둔다.
 */
object UpdateNotice {
    private const val PREF = "update_notice"
    private const val CHANNEL_ID = "update_done"
    private const val ID = 4410

    /** GOAT가 [version] 설치를 시작했다. */
    fun expect(context: Context, version: String) {
        prefs(context).edit().putString("expect", version).commit()
    }

    /** 업데이트를 마쳤다(앱 교체 수신). GOAT가 시작한 업데이트였으면 알림을 띄운다. */
    fun onReplaced(context: Context) {
        if (prefs(context).getString("expect", null) == null) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "업데이트 완료", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goat)
            .setContentTitle(doneText())
            .setContentText("눌러서 열기")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(ID, n)
    }

    /** GOAT를 열었다: 업데이트를 마친 뒤 처음 연 것이면 true(한 번만). 알림도 지운다. */
    fun consume(context: Context): Boolean {
        val p = prefs(context)
        val had = p.getString("expect", null) != null
        if (had) p.edit().remove("expect").apply()
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
        return had
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** "GOAT 1.6.0으로 업데이트했습니다"(버전 끝 숫자 읽는 소리에 맞춰 '로/으로'를 고른다). */
    fun doneText(version: String = BuildConfig.VERSION_NAME) = "GOAT ${version}${ro(version)} 업데이트했습니다"

    /** 숫자로 끝나는 말 뒤의 '로/으로': 받침이 있으면(영·삼·육) '으로', 받침 없거나 ㄹ(일·칠·팔)이면 '로'. */
    internal fun ro(word: String): String = when (word.lastOrNull { it.isLetterOrDigit() }) {
        '0', '3', '6' -> "으로"
        else -> "로"
    }
}

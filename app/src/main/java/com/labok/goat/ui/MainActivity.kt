package com.labok.goat.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.labok.goat.background.GoatService
import com.labok.goat.data.rotate.PowerRotate
import com.labok.goat.data.update.UpdateNotice
import com.labok.goat.ui.home.HomeScreen
import com.labok.goat.ui.liquid.LiquidTheme
import com.labok.goat.ui.tweaks.ResolutionConfirm

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        // Restore an enabled app-event listener when returning after a background-service stop.
        if (PowerRotate.enabled(this)) GoatService.sync(this)
    }

    // Android 12 이하에서 앱에서 고른 언어를 적용한다(13+는 시스템 앱별 언어가 처리)
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.labok.goat.ui.text.AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 업데이트를 마치고 처음 열었으면 알림을 지우고 새 버전을 알린다
        if (savedInstanceState == null && UpdateNotice.consume(this)) {
            Toast.makeText(this, UpdateNotice.doneText(), Toast.LENGTH_SHORT).show()
        }
        setContent {
            // 해상도를 바꾼 뒤 확인 창은 화면이 다시 만들어져도 다시 떠야 해서 맨 위에 둔다
            LiquidTheme {
                // 화면 점검 도구가 넘기는 열 화면·시트(없으면 홈). 처음 만들 때만 따른다
                HomeScreen(
                    openScreen = intent.getStringExtra(EXTRA_SCREEN).takeIf { savedInstanceState == null },
                    openSheet = intent.getStringExtra(EXTRA_SHEET).takeIf { savedInstanceState == null }
                )
                ResolutionConfirm()
            }
        }
    }

    companion object {
        /** 처음 열 기능 화면(NavItem 이름, 예: DPI_MATCH). */
        const val EXTRA_SCREEN = "screen"

        /** 처음 띄울 시트(예: HELP, ABOUT, CHANGELOG). */
        const val EXTRA_SHEET = "sheet"
    }
}

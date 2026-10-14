package com.labok.goat.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.labok.goat.data.update.UpdateNotice
import com.labok.goat.ui.home.HomeScreen
import com.labok.goat.ui.liquid.LiquidTheme
import com.labok.goat.ui.tweaks.ResolutionConfirm

class MainActivity : ComponentActivity() {
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
                HomeScreen()
                ResolutionConfirm()
            }
        }
    }
}

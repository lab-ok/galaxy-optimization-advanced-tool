package com.labok.goat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.labok.goat.ui.home.HomeScreen
import com.labok.goat.ui.liquid.LiquidTheme
import com.labok.goat.ui.tweaks.ResolutionConfirm

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // 해상도를 바꾼 뒤 확인 창은 화면이 다시 만들어져도 다시 떠야 해서 맨 위에 둔다
            LiquidTheme {
                HomeScreen()
                ResolutionConfirm()
            }
        }
    }
}

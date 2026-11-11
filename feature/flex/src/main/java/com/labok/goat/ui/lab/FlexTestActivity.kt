package com.labok.goat.ui.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.labok.goat.ui.liquid.LiquidTheme

/**
 * 플렉스 모드 테스트 화면([FlexTestScreen])을 띄우는 화면. 대화 창으로 띄우면 삼성 기기에서 시스템 막대 크기가 전해지지 않아
 * 아래 [닫기]가 내비게이션 막대에 잘려서, 앱 화면과 같은 방식(가장자리까지 그리고 막대만큼 띄움)으로 따로 띄운다.
 */
class FlexTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LiquidTheme {
                FlexTestScreen(onClose = ::finish)
            }
        }
    }
}

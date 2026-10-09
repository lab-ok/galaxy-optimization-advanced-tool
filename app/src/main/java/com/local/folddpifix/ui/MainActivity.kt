package com.local.folddpifix.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.local.folddpifix.ui.home.HomeScreen
import com.local.folddpifix.ui.liquid.LiquidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LiquidTheme { HomeScreen() }
        }
    }
}

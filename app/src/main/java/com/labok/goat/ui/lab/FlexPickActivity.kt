package com.labok.goat.ui.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labok.goat.data.lab.FlexPanel
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.appsize.AppEntry
import com.labok.goat.ui.appsize.loadApps
import com.labok.goat.ui.liquid.LiquidLoader
import com.labok.goat.ui.liquid.LiquidTheme
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 자동 플렉스 '분할 화면'의 아래 칸: 앱을 고르면 그 앱을 아래 절반에 띄우고 이 화면은 닫힌다([FlexPanel.pickBottom]).
 * 따로 둔 작업(com.labok.goat.flexpick)으로 떠서 GOAT 본 화면과 섞이지 않고, 최근 앱에도 남지 않는다.
 */
class FlexPickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LiquidTheme {
                FlexPickScreen { pkg ->
                    FlexPanel.pickBottom(applicationContext, pkg)
                }
            }
        }
    }
}

@Composable
private fun FlexPickScreen(onPick: (String) -> Unit) {
    val c = LocalLiquid.current
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(Gap.Card))
        Text("아래 칸에 띄울 앱", fontWeight = FontWeight.SemiBold, color = c.ink, style = MaterialTheme.typography.titleMedium)
        Text("최근에 쓴 앱부터 보여 줍니다.", color = c.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Gap.Card))
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LiquidLoader("앱 목록을 불러오는 중…", Modifier.padding(vertical = 12.dp)) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(84.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(Gap.Card),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.pkg }) { app ->
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(app.pkg) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row { Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))) { app.icon?.let { Image(it, null, Modifier.size(48.dp)) } } }
                        Spacer(Modifier.height(Gap.Item))
                        Text(app.label, color = c.ink, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

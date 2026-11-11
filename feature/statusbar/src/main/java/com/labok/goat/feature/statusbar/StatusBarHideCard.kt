package com.labok.goat.feature.statusbar

import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.LiquidSwitchRow
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.nav.NewBadge
import com.labok.goat.ui.nav.NewTag
import com.labok.goat.ui.text.Copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 상태 표시줄 화면의 「상단바 숨기기」 카드. 아이콘만이 아니라 상단바 자리까지 숨겨 앱이 그 자리까지 쓰게 한다(포커스 창 방식, [StatusBarHider]).
 * 상태 표시줄 화면(다른 기능 모듈)이 앱을 거쳐 이 카드를 받아 그린다.
 */
@Composable
fun ColumnScope.StatusBarHideCard() {
    val c = LocalLiquid.current
    val context = LocalContext.current
    NewTag(NewBadge.STATUS_BAR_HIDE, Modifier.padding(bottom = Gap.Item))
    val scope = rememberCoroutineScope()
    val hiding by StatusBarHider.on.collectAsStateWithLifecycle()
    LiquidSwitchRow(Copy.LAB_STATUS_HIDE, Copy.LAB_STATUS_HIDE_BODY, hiding, true) { want ->
        scope.launch {
            val err = withContext(Dispatchers.IO) { if (want) StatusBarHider.start(context) else StatusBarHider.stop(context).let { null } }
            if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        }
    }
    // 주의 문구는 설명 바로 아래에 붙인다(다른 카드처럼 설명 줄과 같은 간격)
    Text(Copy.LAB_STATUS_HIDE_NOTE, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 72.dp))
}

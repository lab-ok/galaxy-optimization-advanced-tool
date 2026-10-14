package com.labok.goat.ui.lab

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

/**
 * 앱이 받는 접힘 정보(Jetpack WindowManager): 반쯤 접혀 접는 선이 가로로 놓였으면(책상에 세운 노트북 모양) [FlexInfo.tabletop],
 * 접는 선의 화면 위치(위에서부터 px)는 [FlexInfo.hingeTop]. 시스템이 앱에 '반쯤 접힘'을 알려 줄 때만 tabletop이 된다.
 */
internal data class FlexInfo(val state: String, val tabletop: Boolean, val hingeTop: Int)

@Composable
internal fun rememberFlexInfo(): State<FlexInfo> {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val flow = remember(activity) {
        if (activity == null) {
            null
        } else {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
                val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                when {
                    fold == null -> FlexInfo("접는 선 없음", false, 0)
                    else -> FlexInfo(
                        if (fold.state == FoldingFeature.State.HALF_OPENED) "반쯤 접힘" else "펼침",
                        fold.state == FoldingFeature.State.HALF_OPENED && fold.orientation == FoldingFeature.Orientation.HORIZONTAL,
                        fold.bounds.top
                    )
                }
            }
        }
    }
    val initial = FlexInfo("알 수 없음", false, 0)
    return flow?.collectAsState(initial) ?: remember { androidx.compose.runtime.mutableStateOf(initial) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

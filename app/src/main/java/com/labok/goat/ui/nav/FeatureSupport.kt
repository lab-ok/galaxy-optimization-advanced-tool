package com.labok.goat.ui.nav

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.labok.goat.data.appsize.DensityShell
import com.labok.goat.data.lab.AutoFlex
import com.labok.goat.data.lab.LabUnlock
import com.labok.goat.data.lab.NarrowApps
import com.labok.goat.data.tweaks.RefreshRate
import com.labok.goat.data.tweaks.Resolution

/**
 * 기능을 이 기기에서 쓸 수 있는지(2026-10-23 소장님 지정): 쓸 수 없는 기능은 사이드바·홈·검색에서 숨긴다.
 * 실험실을 연 사람(염소 10번)에게는 기기와 상관없이 모두 보여 주고, 각 화면에서 '이 기기에서는 쓸 수 없습니다'로 안내한다.
 *
 * 바로 알 수 있는 것(주사율 최고값, 폴드 여부, 모델)은 그때 판정하고, Shizuku로 읽어야 아는 것(앱별 배율 함수, GOS, 촬영음 강제 설정)은
 * 읽은 결과를 이 펌웨어 기준으로 기억해 둔다([note]). 펌웨어가 바뀌면 다시 확인한다. 아직 모르면 보여 준다.
 */
object FeatureSupport {
    private const val PREF = "feature_support"

    /** 기억이 바뀌면 오른다(메뉴를 다시 그리게). */
    var version by mutableIntStateOf(0)
        private set

    /** 읽어 본 결과를 기억한다. [supported]가 false면 이 펌웨어에서 쓸 수 없는 기능으로 둔다. */
    fun note(context: Context, item: NavItem, supported: Boolean) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val key = "no_${item.name}"
        val was = p.getString(key, null) == Build.FINGERPRINT
        if (was == !supported) return
        p.edit().apply { if (supported) remove(key) else putString(key, Build.FINGERPRINT) }.apply()
        version++
    }

    private fun noted(context: Context, item: NavItem) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("no_${item.name}", null) == Build.FINGERPRINT

    /** 이 기기에서 쓸 수 없는 기능인지. */
    fun unsupported(context: Context, item: NavItem): Boolean = when (item) {
        NavItem.REFRESH -> !RefreshRate.supported(context)
        NavItem.RESOLUTION -> !Resolution.supported(context)
        NavItem.LAB_FLEX -> !AutoFlex.supported()
        NavItem.LAB_NARROW -> !NarrowApps.supported()
        NavItem.APP_SIZE -> DensityShell.rejected(context) || noted(context, item)
        NavItem.GOS, NavItem.CAMERA_MUTE -> noted(context, item)
        else -> false
    }

    /** 사이드바·홈에 보일지: 실험실을 열었으면 모두, 아니면 쓸 수 있는 기능만. */
    fun visible(context: Context, item: NavItem): Boolean = LabUnlock.unlocked || !unsupported(context, item)
}

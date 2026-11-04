package com.labok.goat.data.tweaks

import android.content.Context
import com.labok.goat.R
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess
import com.labok.goat.ui.text.Copy

/**
 * 기본 앱 정리: 삼성·제휴 기본 앱을 지우지 않고 '끄기'(pm disable-user)로 멈춘다. 언제든 다시 켤 수 있고, 공장 초기화 없이 되돌린다.
 * pm 명령은 셸 권한 전용이라 Shizuku로 실행한다. 목록은 커뮤니티(디시·클리앙·XDA)에서 많이 끄는 앱 중
 * 꺼도 휴대폰 기본 기능(전화·문자·설정·카메라·잠금화면)이 멈추지 않는 것만 골랐다(docs/research/galaxy-adb-tweaks.md).
 */
object Bloat {
    /** 끄면 생길 수 있는 영향. */
    enum class Risk(private val labelRes: Int) {
        SAFE(R.string.tweaks_bloat_risk_safe),
        CARE(R.string.tweaks_bloat_risk_care);

        val label: String get() = Copy.text(labelRes)
    }

    /** 정리할 수 있는 앱 하나. [note]는 끄면 달라지는 점. */
    data class App(val pkg: String, val name: String, val group: String, val note: String, val risk: Risk = Risk.SAFE)

    /** 화면 언어로 읽은 목록(앱 시작 뒤 읽을 때마다 리소스에서 만든다). */
    val CATALOG: List<App> get() = listOf(
        App("com.samsung.android.app.spage", Copy.text(R.string.tweaks_bloat_spage_name), Copy.text(R.string.tweaks_bloat_group_bixby), Copy.text(R.string.tweaks_bloat_spage_note)),
        App("com.samsung.android.bixby.agent", Copy.text(R.string.tweaks_bloat_bixby_voice_name), Copy.text(R.string.tweaks_bloat_group_bixby), Copy.text(R.string.tweaks_bloat_bixby_voice_note), Risk.CARE),
        App("com.samsung.android.bixby.wakeup", Copy.text(R.string.tweaks_bloat_bixby_wakeup_name), Copy.text(R.string.tweaks_bloat_group_bixby), Copy.text(R.string.tweaks_bloat_bixby_wakeup_note)),
        App("com.samsung.android.bixbyvision.framework", Copy.text(R.string.tweaks_bloat_bixby_vision_name), Copy.text(R.string.tweaks_bloat_group_bixby), Copy.text(R.string.tweaks_bloat_bixby_vision_note)),
        App("com.samsung.android.visionintelligence", Copy.text(R.string.tweaks_bloat_vision_intelligence_name), Copy.text(R.string.tweaks_bloat_group_bixby), Copy.text(R.string.tweaks_bloat_vision_intelligence_note)),
        App("com.samsung.android.game.gamehome", Copy.text(R.string.tweaks_bloat_gamehome_name), Copy.text(R.string.tweaks_bloat_group_game), Copy.text(R.string.tweaks_bloat_gamehome_note)),
        App("com.samsung.android.game.gos", Copy.text(R.string.tweaks_bloat_gos_name), Copy.text(R.string.tweaks_bloat_group_game), Copy.text(R.string.tweaks_bloat_gos_note), Risk.CARE),
        App("com.samsung.android.arzone", Copy.text(R.string.tweaks_bloat_arzone_name), "AR", Copy.text(R.string.tweaks_bloat_arzone_note)),
        App("com.samsung.android.aremoji", Copy.text(R.string.tweaks_bloat_aremoji_name), "AR", Copy.text(R.string.tweaks_bloat_aremoji_note)),
        App("com.samsung.android.aremojieditor", Copy.text(R.string.tweaks_bloat_aremoji_editor_name), "AR", Copy.text(R.string.tweaks_bloat_aremoji_editor_note)),
        App("com.samsung.android.stickercenter", Copy.text(R.string.tweaks_bloat_sticker_center_name), "AR", Copy.text(R.string.tweaks_bloat_sticker_center_note)),
        App("com.samsung.android.app.tips", Copy.text(R.string.tweaks_bloat_tips_name), Copy.text(R.string.tweaks_bloat_group_samsung_service), Copy.text(R.string.tweaks_bloat_tips_note)),
        App("com.samsung.android.voc", "Samsung Members", Copy.text(R.string.tweaks_bloat_group_samsung_service), Copy.text(R.string.tweaks_bloat_members_note)),
        App("com.samsung.android.service.peoplestripe", Copy.text(R.string.tweaks_bloat_edge_people_name), Copy.text(R.string.tweaks_bloat_group_samsung_service), Copy.text(R.string.tweaks_bloat_edge_people_note)),
        App("com.samsung.android.dynamiclock", Copy.text(R.string.tweaks_bloat_dynamic_lock_name), Copy.text(R.string.tweaks_bloat_group_samsung_service), Copy.text(R.string.tweaks_bloat_dynamic_lock_note)),
        App("com.samsung.android.kidsinstaller", Copy.text(R.string.tweaks_bloat_kids_installer_name), Copy.text(R.string.tweaks_bloat_group_samsung_service), Copy.text(R.string.tweaks_bloat_kids_installer_note)),
        App("com.sec.android.app.kidshome", Copy.text(R.string.tweaks_bloat_kids_home_name), Copy.text(R.string.tweaks_bloat_group_samsung_service), Copy.text(R.string.tweaks_bloat_kids_home_note)),
        App("com.samsung.android.da.daagent", Copy.text(R.string.tweaks_bloat_dual_messenger_name), Copy.text(R.string.tweaks_bloat_group_samsung_service), Copy.text(R.string.tweaks_bloat_dual_messenger_note), Risk.CARE),
        App("com.sec.android.easyMover", "Smart Switch", Copy.text(R.string.tweaks_bloat_group_samsung_service), Copy.text(R.string.tweaks_bloat_smart_switch_note)),
        App("com.samsung.android.app.watchmanagerstub", Copy.text(R.string.tweaks_bloat_wearable_stub_name), Copy.text(R.string.tweaks_bloat_group_samsung_service), Copy.text(R.string.tweaks_bloat_wearable_stub_note)),
        App("com.sec.android.app.sbrowser", Copy.text(R.string.tweaks_bloat_internet_name), Copy.text(R.string.tweaks_bloat_group_samsung_app), Copy.text(R.string.tweaks_bloat_internet_note)),
        App("com.samsung.android.email.provider", Copy.text(R.string.tweaks_bloat_email_name), Copy.text(R.string.tweaks_bloat_group_samsung_app), Copy.text(R.string.tweaks_bloat_email_note)),
        App("com.samsung.android.spay", Copy.text(R.string.tweaks_bloat_wallet_name), Copy.text(R.string.tweaks_bloat_group_samsung_app), Copy.text(R.string.tweaks_bloat_wallet_note), Risk.CARE),
        App("com.samsung.android.scloud", Copy.text(R.string.tweaks_bloat_cloud_name), Copy.text(R.string.tweaks_bloat_group_samsung_app), Copy.text(R.string.tweaks_bloat_cloud_note), Risk.CARE),
        App("com.facebook.appmanager", Copy.text(R.string.tweaks_bloat_fb_appmanager_name), Copy.text(R.string.tweaks_bloat_group_partner), Copy.text(R.string.tweaks_bloat_fb_appmanager_note)),
        App("com.facebook.services", Copy.text(R.string.tweaks_bloat_fb_services_name), Copy.text(R.string.tweaks_bloat_group_partner), Copy.text(R.string.tweaks_bloat_fb_services_note)),
        App("com.facebook.system", Copy.text(R.string.tweaks_bloat_fb_system_name), Copy.text(R.string.tweaks_bloat_group_partner), Copy.text(R.string.tweaks_bloat_fb_system_note)),
        App("com.microsoft.skydrive", "OneDrive", Copy.text(R.string.tweaks_bloat_group_partner), Copy.text(R.string.tweaks_bloat_onedrive_note)),
        App("com.linkedin.android", "LinkedIn", Copy.text(R.string.tweaks_bloat_group_partner), Copy.text(R.string.tweaks_bloat_linkedin_note)),
        App("com.netflix.partner.activation", Copy.text(R.string.tweaks_bloat_netflix_activation_name), Copy.text(R.string.tweaks_bloat_group_partner), Copy.text(R.string.tweaks_bloat_netflix_activation_note))
    )

    /** 이 기기에 있는 목록 앱과 꺼져 있는지(작업 스레드). Shizuku가 없으면 null. */
    fun scan(): Map<String, Boolean>? {
        val all = ShizukuAccess.shell("pm list packages --user 0") ?: return null
        if (!all.ok) return null
        val off = ShizukuAccess.shell("pm list packages -d --user 0")?.out.orEmpty()
        fun names(text: String) = text.lineSequence().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
        val installed = names(all.out)
        val disabled = names(off)
        return CATALOG.filter { it.pkg in installed }.associate { it.pkg to (it.pkg in disabled) }
    }

    /** 끄거나(off=true) 다시 켠다(작업 스레드). 성공하면 true. */
    fun set(context: Context, app: App, off: Boolean, how: String): Boolean {
        val cmd = if (off) "pm disable-user --user 0 ${app.pkg}" else "pm enable --user 0 ${app.pkg}"
        val r = ShizukuAccess.shell(cmd)
        val ok = r?.ok == true && (r.out.contains("disabled") || r.out.contains("enabled") || r.out.isEmpty())
        // GOS는 전용 화면(GOS 끄기)과 상태를 함께 쓴다: 어느 화면에서 바꿔도 다시 끄기의 기준을 맞춘다
        if (ok) Gos.noteChange(context, app.pkg, off)
        LogRepository.from(context).add(
            "기본 앱 정리($how): ${app.name}(${app.pkg}) ${if (off) "끄기" else "켜기"} " +
                if (ok) "성공" else "실패(${r?.err?.ifBlank { null } ?: r?.out ?: "Shizuku 없음"})"
        )
        return ok
    }
}

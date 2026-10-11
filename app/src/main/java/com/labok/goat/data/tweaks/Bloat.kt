package com.labok.goat.data.tweaks

import android.content.Context
import com.labok.goat.data.log.LogRepository
import com.labok.goat.data.shizuku.ShizukuAccess

/**
 * 기본 앱 정리: 삼성·제휴 기본 앱을 지우지 않고 '끄기'(pm disable-user)로 멈춘다. 언제든 다시 켤 수 있고, 공장 초기화 없이 되돌린다.
 * pm 명령은 셸 권한 전용이라 Shizuku로 실행한다. 목록은 커뮤니티(디시·클리앙·XDA)에서 많이 끄는 앱 중
 * 꺼도 휴대폰 기본 기능(전화·문자·설정·카메라·잠금화면)이 멈추지 않는 것만 골랐다(docs/research/galaxy-adb-tweaks.md).
 */
object Bloat {
    /** 끄면 생길 수 있는 영향. */
    enum class Risk(val label: String) {
        SAFE("안전"),
        CARE("주의"),
    }

    /** 정리할 수 있는 앱 하나. [note]는 끄면 달라지는 점. */
    data class App(val pkg: String, val name: String, val group: String, val note: String, val risk: Risk = Risk.SAFE)

    val CATALOG: List<App> = listOf(
        App("com.samsung.android.app.spage", "삼성 프리(왼쪽 화면 피드)", "빅스비", "홈 화면 왼쪽으로 넘겼을 때 나오는 피드가 사라집니다."),
        App("com.samsung.android.bixby.agent", "빅스비 보이스", "빅스비", "측면 버튼 길게 누르기를 '전원 끄기 메뉴'로 바꿔 두세요. 빅스비 음성을 쓸 수 없습니다.", Risk.CARE),
        App("com.samsung.android.bixby.wakeup", "빅스비 호출어(하이 빅스비)", "빅스비", "'하이 빅스비'로 부를 수 없습니다."),
        App("com.samsung.android.bixbyvision.framework", "빅스비 비전", "빅스비", "카메라의 빅스비 비전(사물 인식)을 쓸 수 없습니다."),
        App("com.samsung.android.visionintelligence", "빅스비 비전 지능", "빅스비", "빅스비 비전과 함께 끄는 구성 요소입니다."),
        App("com.samsung.android.game.gamehome", "게임 런처", "게임", "게임 모음 화면과 게임 중 도구 모음이 사라집니다."),
        App("com.samsung.android.game.gos", "게임 최적화 서비스(GOS)", "게임", "게임 중 성능 제한이 풀려 더 빠르지만 발열·배터리 소모가 늘 수 있습니다. 재부팅하면 다시 켜지는 기기도 있습니다.", Risk.CARE),
        App("com.samsung.android.arzone", "AR 존", "AR", "AR 이모지·AR 낙서 등 AR 기능 모음이 사라집니다."),
        App("com.samsung.android.aremoji", "AR 이모지", "AR", "AR 이모지를 만들 수 없습니다."),
        App("com.samsung.android.aremojieditor", "AR 이모지 편집기", "AR", "AR 이모지 꾸미기를 쓸 수 없습니다."),
        App("com.samsung.android.stickercenter", "스티커 센터", "AR", "카메라·키보드의 추가 스티커를 받을 수 없습니다."),
        App("com.samsung.android.app.tips", "사용 팁", "삼성 서비스", "삼성 사용 팁 알림과 앱이 사라집니다."),
        App("com.samsung.android.voc", "Samsung Members", "삼성 서비스", "삼성 멤버스(오류 신고·커뮤니티)를 쓸 수 없습니다."),
        App("com.samsung.android.service.peoplestripe", "엣지 피플", "삼성 서비스", "엣지 패널의 '연락처' 패널이 사라집니다."),
        App("com.samsung.android.dynamiclock", "다이내믹 잠금화면", "삼성 서비스", "잠금화면 배경 자동 바꾸기를 쓸 수 없습니다."),
        App("com.samsung.android.kidsinstaller", "삼성 키즈 설치", "삼성 서비스", "키즈 모드를 설치·사용할 수 없습니다."),
        App("com.sec.android.app.kidshome", "삼성 키즈 홈", "삼성 서비스", "키즈 모드를 쓸 수 없습니다."),
        App("com.samsung.android.da.daagent", "듀얼 메신저", "삼성 서비스", "메신저 앱을 두 개 쓰는 기능이 꺼집니다. 이미 만든 두 번째 앱이 있으면 끄지 마세요.", Risk.CARE),
        App("com.sec.android.easyMover", "Smart Switch", "삼성 서비스", "다른 폰에서 데이터를 옮겨 올 때만 필요합니다. 옮길 때 다시 켜세요."),
        App("com.samsung.android.app.watchmanagerstub", "갤럭시 웨어러블 설치 도우미", "삼성 서비스", "갤럭시 워치·버즈 연결 앱을 자동으로 설치하지 않습니다."),
        App("com.sec.android.app.sbrowser", "삼성 인터넷", "삼성 앱", "삼성 인터넷 브라우저를 쓸 수 없습니다. 다른 브라우저를 기본으로 정해 두세요."),
        App("com.samsung.android.email.provider", "삼성 이메일", "삼성 앱", "삼성 이메일 앱을 쓸 수 없습니다."),
        App("com.samsung.android.spay", "삼성 월렛(페이)", "삼성 앱", "삼성페이 결제·교통카드·멤버십을 쓸 수 없습니다. 결제에 쓰면 끄지 마세요.", Risk.CARE),
        App("com.samsung.android.scloud", "삼성 클라우드", "삼성 앱", "삼성 클라우드 백업·동기화가 멈춥니다.", Risk.CARE),
        App("com.facebook.appmanager", "Facebook 앱 관리자", "제휴 앱", "페이스북 앱 자동 업데이트 구성 요소입니다."),
        App("com.facebook.services", "Facebook 서비스", "제휴 앱", "페이스북 백그라운드 서비스입니다."),
        App("com.facebook.system", "Facebook 앱 설치 도우미", "제휴 앱", "페이스북 앱을 미리 설치하는 구성 요소입니다."),
        App("com.microsoft.skydrive", "OneDrive", "제휴 앱", "마이크로소프트 OneDrive를 쓸 수 없습니다."),
        App("com.linkedin.android", "LinkedIn", "제휴 앱", "링크드인 앱을 쓸 수 없습니다."),
        App("com.netflix.partner.activation", "Netflix 활성화 도우미", "제휴 앱", "넷플릭스 미리 설치 구성 요소입니다(넷플릭스 앱은 그대로)."),
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
        LogRepository.from(context).add(
            "기본 앱 정리($how): ${app.name}(${app.pkg}) ${if (off) "끄기" else "켜기"} " +
                if (ok) "성공" else "실패(${r?.err?.ifBlank { null } ?: r?.out ?: "Shizuku 없음"})",
        )
        return ok
    }
}

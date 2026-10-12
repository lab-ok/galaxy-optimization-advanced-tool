package com.labok.goat.ui.about

import com.labok.goat.ui.components.SheetColumn
import com.labok.goat.ui.text.Copy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.liquid.GlassCard
import com.labok.goat.ui.liquid.LocalLiquid

/** 변경 기록 한 판. [sections]는 (묶음 이름, 바뀐 점들). */
internal data class Release(val version: String, val date: String, val summary: String, val sections: List<Pair<String, List<String>>>)

/** 판마다 바뀐 점. 새 판은 맨 앞에 더한다. */
internal val CHANGELOG = listOf(
    Release(
        "GOAT 1.4.0", "2026-10-13", "핫스팟 제한 우회를 실험실로 옮기고, 초보자도 버튼 하나로 쓰고 언제든 되돌릴 수 있게 다시 만들었습니다.",
        listOf(
            "실험실" to listOf(
                "사이드바에 '실험실' 묶음을 만들고 핫스팟 제한 우회를 옮겼습니다. 기기·통신사마다 결과가 다를 수 있는 기능을 모읍니다.",
                "[제한 풀기] 버튼 하나로 테더링 APN을 추가하고, 끝나면 핫스팟 설정을 바로 엽니다.",
                "지금 쓰는 APN을 여러 경로(데이터 유심의 기본 APN, 통신사 APN 목록, 국내 통신사 기본값)로 찾고, 실패하면 이유를 화면에 보여 줍니다.",
                "자동 추가가 막힌 기기에서는 ①~⑤ 순서대로 따라 하는 직접 하기 안내가 나타납니다.",
                "되돌리기: [원래대로]는 GOAT 테더링 APN만 지우고, [APN 전체 기본값으로]는 APN 목록 전체를 통신사 기본값으로 되돌립니다. 언제든 화면에 보입니다.",
            ),
        ),
    ),
    Release(
        "GOAT 1.3.7", "2026-10-13", "핫스팟 제한 우회를 더했습니다(처음 쓸 때 경고에 동의해야 합니다).",
        listOf(
            "새 기능" to listOf(
                "핫스팟 제한 우회: 지금 쓰는 인터넷 APN과 같은 값에 유형만 dun인 'GOAT 테더링' APN을 더해 핫스팟이 일반 데이터 경로를 쓰게 합니다(Shizuku).",
                "자동 추가가 막힌 기기에서는 숨겨진 APN 추가 화면을 열고 이름·APN·전각 ｄｕｎ을 복사해 넣게 안내합니다. 추가한 APN은 언제든 지울 수 있습니다.",
                "통신사 약관 위반 소지와 책임을 처음에 알리고, 동의해야 추가 버튼이 나타납니다.",
            ),
        ),
    ),
    Release(
        "GOAT 1.3.6", "2026-10-12", "삼성 글꼴 스타일(기기 글꼴)이 앱에도 적용되게 하고, 글자를 아주 조금 더 줄였습니다.",
        listOf(
            "화면" to listOf(
                "설정 → 디스플레이 → 글꼴 스타일에서 고른 글꼴(내려받은 글꼴 포함)이 GOAT 화면에도 적용됩니다. 굵은 글씨도 같은 글꼴의 굵은 굵기로 나옵니다.",
                "앱 전체 글자 크기를 아주 조금 더 줄였습니다.",
            ),
        ),
    ),
    Release(
        "GOAT 1.3.5", "2026-10-12", "앱 전체의 간격을 하나의 규칙표로 맞추고, 글자를 조금 작게 했습니다.",
        listOf(
            "화면" to listOf(
                "간격 규칙표를 만들어 모든 화면에 적용했습니다. 글과 조작(조절바·칩·버튼·그림) 사이는 위아래 모두 같은 간격, 다음 설정 묶음 앞은 한 단계 넓은 간격, 제목과 설명·설명 문단 사이는 줄 간격만 둡니다.",
                "조절바·칩 바로 아래 설명이 붙어 보이던 곳(앱별 화면 배율 홈 아이콘 크기 등)을 모두 고쳤습니다.",
                "글자와 버튼에 따로 붙어 있던 위쪽 여백을 없애고 같은 간격 값으로 바꿨습니다. 규칙은 테스트로 지켜집니다.",
                "앱 전체 글자 크기를 조금 줄였습니다(시스템 글자 크기 설정은 그대로 따릅니다).",
            ),
        ),
    ),
    Release(
        "GOAT 1.3.4", "2026-10-12", "변경 기록을 판별로 접고 펴게 하고, 누르는 줄 목록의 위아래 여백을 다른 카드와 맞췄습니다.",
        listOf(
            "화면" to listOf(
                "변경 기록: 판마다 접고 펴는 아코디언으로 바꾸고, 처음에는 가장 최근 판만 펼쳐 둡니다.",
                "누르는 줄 목록(앱 설정 정보, 해상도·주사율 고르기)의 위아래 여백이 다른 카드보다 넓던 것을 같은 규칙으로 맞췄습니다.",
            ),
        ),
    ),
    Release(
        "GOAT 1.3.3", "2026-10-12", "UI 유연성과 햅틱 세기의 기본값을 조정하고, UI 유연성이 모든 리퀴드 요소에 적용되게 했습니다.",
        listOf(
            "앱 설정" to listOf(
                "UI 유연성과 햅틱 세기: 예전 75%를 새 100%(기본값)로 삼고, 조절바는 0~200%로 넓혔습니다. 이미 바꿔 둔 값은 같은 느낌이 되도록 새 눈금으로 옮깁니다.",
                "UI 유연성이 라디오 버튼, 자동 맞추기의 흔들림 점, 누를 수 없는 버튼의 흔들림까지 모든 리퀴드 요소에 적용됩니다.",
                "정보 항목(앱 정보·변경 기록·필요 권한)을 제목과 세부 내용 두 줄로 보여 줍니다.",
                "새 버전 확인을 홈으로 돌아올 때도(30분에 한 번) 하고, 새 버전이 있으면 홈 맨 위에 업데이트 카드를 띄웁니다.",
                "기록 파일 남기기의 설명을 다듬었습니다.",
            ),
            "화면" to listOf(
                "라디오(해상도·주사율 고르기)를 리퀴드 엔진으로 바꿨습니다. 고른 방울이 다음 칸으로 늘어나며 끌려가고 꼬리가 뒤따라 합쳐진 뒤 한 번 출렁입니다.",
                "홈 타일 칸 수를 기능 이름이 한 줄에 다 보이는 폭에 맞춰 정합니다(펼친 폴드에서는 두 칸).",
                "홈에서 꺼진 기능은 아이콘을 살짝 연하게 보여 주고, 화면 제목줄에 메뉴 아이콘을 넣었습니다.",
                "사이드바의 앱 설정도 다른 메뉴처럼 고르면 검은 방울이 옮겨 갑니다.",
                "조절바: 값 글자 폭과 상관없이 트랙 폭을 모두 같게 하고, 위쪽 여백을 줄였습니다. 화면을 세로로 스크롤할 때는 조절바가 바뀌지 않습니다.",
                "글 간격 규칙을 정했습니다. 설명 줄 간격은 제목과 설명 사이보다 좁고, 같은 설명 문단 사이에는 따로 띄우지 않습니다.",
            ),
        ),
    ),
    Release(
        "GOAT 1.3.2", "2026-10-12", "UI 탄성을 UI 유연성으로 바꾸고, 줄이면 속도는 그대로 두고 출렁임만 줄도록 고쳤습니다.",
        listOf(
            "앱 설정" to listOf(
                "UI 유연성(이전 이름 UI 탄성): 줄여도 움직이는 속도는 그대로이고, 모양이 단단해져 출렁임만 줄어듭니다. 0%면 딱딱하게 움직입니다.",
                "앱 설정을 '설정'(업데이트·UI 유연성·햅틱 세기·기록 파일)과 '정보'(앱 정보·변경 기록·필요 권한)로 나눴습니다.",
                "필요 권한 안내에 인터넷(업데이트 확인용) 사용을 바르게 적었습니다.",
            ),
        ),
    ),
    Release(
        "GOAT 1.3.1", "2026-10-12", "앱 설정 화면을 만들고 UI 탄성과 햅틱 세기를 조절할 수 있게 했습니다.",
        listOf(
            "앱 설정" to listOf(
                "사이드바 맨 아래 '앱 정보'를 '앱 설정'으로 바꿨습니다. 앱 정보, 업데이트 확인, UI 탄성, 햅틱 세기, 기록 파일 남기기를 한곳에 모았습니다.",
                "UI 탄성: 스위치·카드·메뉴·사이드바가 움직인 뒤 출렁이는 정도를 0%(바로 멈춤)부터 150%까지 조절합니다.",
                "햅틱 세기: 누르고 켜고 고를 때의 진동을 0%(끔)부터 150%까지 조절합니다. 기기의 터치 진동 설정도 따릅니다.",
                "기록 파일 남기기는 홈 맨 아래에서 앱 설정으로 옮겼습니다.",
            ),
        ),
    ),
    Release(
        "GOAT 1.3", "2026-10-12", "앱 안에서 바로 업데이트하고, 홈 타일을 한눈에 보기 쉽게 정리했습니다.",
        listOf(
            "새 기능" to listOf(
                "앱 안 업데이트: 홈을 열면 GitHub 릴리스에서 새 버전을 확인하고, 있으면 홈 위쪽에 [지금 업데이트]를 띄웁니다. 앱 정보에서도 직접 확인할 수 있습니다.",
                "Shizuku가 연결돼 있으면 묻지 않고 바로 설치하고, 없으면 시스템 설치 창으로 설치합니다. 내려받은 파일은 크기와 SHA-256이 릴리스와 같을 때만 설치합니다.",
            ),
            "홈" to listOf(
                "타일의 기능 이름과 지금 상태를 아이콘 오른쪽에 한 줄씩 두고, 칸보다 긴 글은 천천히 흘러가며 보여 줍니다.",
                "켜짐 알약을 빼고 굵은 테두리로만 켜짐을 표시해, 타일의 빈칸을 없애고 높이를 모두 맞췄습니다.",
            ),
            "권한" to listOf(
                "업데이트 확인과 내려받기를 위해 인터넷 권한을, Shizuku가 없을 때 설치 창을 띄우려고 앱 설치 권한을 더했습니다. 그 밖의 통신은 하지 않습니다.",
            ),
        ),
    ),
    Release(
        "GOAT 1.2", "2026-10-12", "잠자기 모드 끄기를 더하고, 작동 기록 파일은 기본으로 남기지 않습니다.",
        listOf(
            "새 기능" to listOf(
                "잠자기 모드 끄기: 화면이 꺼진 동안 앱 동작을 몰아서 늦추는 잠자기 모드(Doze)를 꺼 알림이 늦게 오지 않게 합니다. " +
                    "'adb shell dumpsys deviceidle disable'과 같은 방법이며, 재부팅하면 다시 켜지는 설정이라 부팅마다 Shizuku가 연결될 때 다시 끕니다(Shizuku).",
            ),
            "기록 파일" to listOf(
                "기록 파일 남기기: 홈 맨 아래에 스위치를 두고 기본은 끔으로 했습니다. 끄면 다운로드 폴더에 작동 기록 파일을 만들지 않습니다.",
                "기기에 따라 작동 기록 파일이 '(1)'·'(2)'처럼 번호를 붙여 계속 새로 생기던 문제를 고쳤습니다(새 파일은 하루 한 번만 만듭니다).",
            ),
        ),
    ),
    Release(
        "GOAT 1.1", "2026-10-12", "FoldFit 1.0에서 이름을 바꾸고 갤럭시 도구 모음으로 넓혔습니다.",
        listOf(
            "이름과 모습" to listOf(
                "앱 이름을 FoldFit에서 GOAT(Galaxy Optimization Advanced Tool)로 바꾸고, 염소 아이콘을 새로 그렸습니다.",
                "앱 ID가 바뀌어(com.labok.goat) FoldFit과 다른 앱으로 설치됩니다.",
                "홈 화면: 모든 기능의 켜짐·꺼짐과 지금 상태를 리퀴드 카드로 한눈에 보고, 켜진 기능은 굵은 테두리로 표시합니다.",
                "사이드바를 홈·화면 배율·화면 표시·편의 기능·시스템으로 묶고, 폴드 내부 화면·태블릿에서는 늘 펼쳐 둡니다.",
                "모든 기능 화면을 소개 → 권한 → 주 조작 → 세부 옵션 → 고급 설정 → 주의 순서로 맞추고, 사용 방법 순서도 통일했습니다.",
                "홈 카드 높이를 모두 같게 맞추고, 켜진 기능 카드가 처음 나타날 때 동그랗게 부풀었다 터지던 문제를 고쳤습니다.",
                "사이드바나 팝업이 열리면 뒤 화면을 흐리고, 덮개는 반투명 유리처럼 비치게 했습니다.",
                "앱 목록 등을 불러오는 동안 리퀴드 로딩 방울을 보여 주고, 점 세 개 메뉴의 '초기화'는 구분선으로 떼어 두었습니다.",
                "염소 아이콘을 누르면 찡그리고, 계속 누르면 그렁그렁 우는 얼굴과 화난 얼굴이 번갈아 나옵니다.",
                "스위치·조절바·메뉴·사이드바·카드를 연체 물리 물방울(리퀴드)로 다시 만들었습니다.",
                "모든 사용 방법에 움직이는 그림을 넣었고, 카드 간격·여백을 한 기준으로 맞췄습니다.",
                "조절바: 붙들고 0.75초 가만히 있으면 리퀴드 확대 패널이 열려 앞뒤 10칸을 세밀하게 고르고, 손을 뗄 때 값이 밀리지 않습니다.",
                "누를 때마다 시스템 터치 진동을 따르는 촉각 피드백을 줍니다.",
            ),
            "새 기능" to listOf(
                "앱별 화면 배율 설정: 앱마다 화면 배율을 따로 정하고, 알림창에서 지금 앱의 배율을 바로 바꿉니다(One UI 9 이상, Shizuku).",
                "흔들어서 화면 회전: 평소에는 화면 회전을 잠가 두고, 기기를 흔들 때에만 현재 방향으로 회전한 뒤 다시 잠급니다. 민감도·고급 설정(큰 화면에서만 포함)·자동 맞추기·흔든 리듬 진동. 자동 맞추기의 물방울은 흔들림·충격이 잡히면 튀고, 물의 양·점도·표면장력·흩어짐 등을 조절바로 바꿉니다.",
                "카메라 촬영음 끄기: 국내 모델의 강제 촬영음을 끄고, 업데이트로 다시 켜지면 자동으로 다시 끕니다.",
                "애니메이션 속도: 창·화면 전환·앱 안 움직임 속도를 한 번에 또는 하나씩 바꿉니다.",
                "기본 앱 정리: 쓰지 않는 기본 앱을 지우지 않고 꺼 두고, 언제든 다시 켭니다.",
                "홈 화면 아이콘 크기: 홈 화면 앱에만 배율을 걸어 홈 화면 아이콘·글자만 작게(크게) 합니다.",
                "주사율 고정: 항상 최고 주사율 또는 60Hz 절전으로 고정하고, 지금 화면 주사율을 실시간으로 봅니다(Shizuku).",
                "해상도 낮추기: 글자·아이콘 크기는 그대로 두고 해상도만 낮추며, 15초 안에 확인하지 않으면 원래대로 돌아갑니다(폴드 제외).",
                "앱 다크 모드 강제: 다크 모드가 없는 앱도 시스템이 어둡게 그리게 합니다. 켜면 시스템 다크 모드도 함께 켜고, 끄면 원래대로 돌립니다. 일부 앱에서 생길 수 있는 화면 오류를 미리 안내합니다(Shizuku).",
                "GOS 끄기: 게임 중 성능을 조절하는 게임 최적화 서비스를 끄고, 재부팅 뒤 다시 켜지면 다시 끕니다(Shizuku).",
            ),
            "연결과 권한" to listOf(
                "Shizuku로 PC 없이 시스템 설정 권한을 받습니다. 연결 방법은 Shizuku·PC 무선·PC 유선 중에서 고르고, 단계별 그림 안내를 따릅니다.",
                "Shizuku가 켜지면 무선 디버깅을 자동으로 끄는 옵션을 넣었습니다.",
                "필요 권한 목록에 Shizuku·진동 권한을 더하고, 접근성 권한과 인터넷 권한은 쓰지 않습니다.",
            ),
            "고급 화면 배율 설정" to listOf(
                "메뉴 이름을 '화면 크기 맞추기'에서 '고급 화면 배율 설정'으로 바꿨습니다.",
                "표시 크기는 조절바로 값만 고르고 [적용]을 눌러야 반영됩니다.",
                "처음 설치해 저장된 값이 없을 때 [적용] 버튼이 나오지 않던 문제를 고쳤습니다.",
                "DPI를 120부터 이 기기 화면에 맞는 최대값(짧은 변 320dp 기준)까지 1 단위로 고릅니다. 태블릿처럼 큰 화면도 넓게 쓸 수 있습니다.",
                "폴드가 아닌 기기에서는 DPI 하나만 정하고, 두 화면 맞추기(내부 화면 보정·크기 테스트·접기 감시)는 숨깁니다.",
            ),
            "알림·문제 신고·로그" to listOf(
                "GOAT 알림은 하나만 띄우고, 제목은 기능 이름·내용은 한 줄로 정리했습니다. 알림 아이콘도 염소로 바꿨습니다.",
                "문제 신고는 메일 앱을 바로 열고, 진단 파일과 오늘 로그를 첨부합니다.",
                "설정을 바꿀 때마다 로그에 남기고, 로그 파일이 기록마다 새로 생기던 문제를 고쳤습니다(다운로드/GOAT).",
            ),
        ),
    ),
    Release(
        "FoldFit 1.0", "2026-10-10", "첫 공개 판.",
        listOf(
            "기능" to listOf(
                "폴드 외부·내부 화면의 DPI를 두 화면의 PPI로 계산해 같은 크기로 맞추고, 재부팅·접기/펼치기 뒤 다시 적용합니다.",
                "다른 설정에서 DPI가 바뀌면 그 값을 기준으로 할지 복원할지 묻습니다. 크기 테스트·PC 권한 안내·문제 신고.",
            ),
        ),
    ),
)

/**
 * 변경 기록(앱 설정 → 정보): 판마다 접고 펴는 아코디언. 머리에는 판 이름·날짜·한 줄 요약, 펼치면 묶음별 바뀐 점.
 * 처음에는 가장 최근 판만 펼쳐 둔다.
 */
@Composable
internal fun ChangelogSheet() {
    SheetColumn(Copy.INFO_CHANGELOG) {
        CHANGELOG.forEachIndexed { i, r ->
            if (i > 0) Spacer(Modifier.height(Gap.Card))
            ReleaseCard(r, initiallyOpen = i == 0)
        }
    }
}

/** 판 하나(아코디언 한 칸). */
@Composable
private fun ReleaseCard(r: Release, initiallyOpen: Boolean) {
    val c = LocalLiquid.current
    var open by remember { mutableStateOf(initiallyOpen) }
    GlassCard {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${r.version} · ${r.date}", fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(r.summary, color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                r.sections.forEach { (title, items) ->
                    Spacer(Modifier.height(Gap.Item))
                    Text(title, fontWeight = FontWeight.SemiBold, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    items.forEach { line ->
                        Row(Modifier.padding(top = Gap.Tight)) {
                            Text("•  ", color = c.muted, style = MaterialTheme.typography.bodySmall)
                            Text(line, color = c.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

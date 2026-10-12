package com.labok.goat.ui.about

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
        "GOAT 1.1", "2026-10-12", "FoldFit 1.0에서 이름을 바꾸고 갤럭시 도구 모음으로 넓혔습니다.",
        listOf(
            "이름과 모습" to listOf(
                "앱 이름을 FoldFit에서 GOAT(Galaxy Optimization Advanced Tool)로 바꾸고, 염소 아이콘을 새로 그렸습니다.",
                "앱 ID가 바뀌어(com.labok.goat) FoldFit과 다른 앱으로 설치됩니다.",
                "홈 화면: 모든 기능의 켜짐·꺼짐과 지금 상태를 리퀴드 카드로 한눈에 보고, 켜진 기능은 굵은 테두리로 표시합니다.",
                "사이드바를 홈·화면 배율·화면 표시·편의 기능·시스템으로 묶고, 폴드 내부 화면·태블릿에서는 늘 펼쳐 둡니다.",
                "스위치·조절바·메뉴·사이드바·카드를 연체 물리 물방울(리퀴드)로 다시 만들었습니다.",
                "모든 사용 방법에 움직이는 그림을 넣었고, 카드 간격·여백을 한 기준으로 맞췄습니다.",
                "조절바: 붙들고 가만히 있으면 리퀴드 확대 패널이 열려 앞뒤 10칸을 세밀하게 고르고, 손을 뗄 때 값이 밀리지 않습니다.",
                "누를 때마다 시스템 터치 진동을 따르는 촉각 피드백을 줍니다.",
            ),
            "새 기능" to listOf(
                "앱별 화면 배율 설정: 앱마다 화면 배율을 따로 정하고, 알림창에서 지금 앱의 배율을 바로 바꿉니다(One UI 9 이상, Shizuku).",
                "흔들어서 화면 회전: 평소에는 화면 회전을 잠가 두고, 기기를 흔들 때에만 현재 방향으로 회전한 뒤 다시 잠급니다. 민감도·고급 설정(큰 화면에서만 포함)·자동 맞추기·흔든 리듬 진동.",
                "카메라 촬영음 끄기: 국내 모델의 강제 촬영음을 끄고, 업데이트로 다시 켜지면 자동으로 다시 끕니다.",
                "애니메이션 속도: 창·화면 전환·앱 안 움직임 속도를 한 번에 또는 하나씩 바꿉니다.",
                "기본 앱 정리: 쓰지 않는 기본 앱을 지우지 않고 꺼 두고, 언제든 다시 켭니다.",
                "홈 화면 아이콘 크기: 홈 화면 앱에만 배율을 걸어 홈 화면 아이콘·글자만 작게(크게) 합니다.",
                "주사율 고정: 항상 최고 주사율 또는 60Hz 절전으로 고정하고, 지금 화면 주사율을 실시간으로 봅니다(Shizuku).",
                "해상도 낮추기: 글자·아이콘 크기는 그대로 두고 해상도만 낮추며, 15초 안에 확인하지 않으면 원래대로 돌아갑니다(폴드 제외).",
                "앱 다크 모드 강제: 다크 모드가 없는 앱도 시스템이 어둡게 그리게 합니다. 켜면 시스템 다크 모드도 함께 켜고, 끄면 원래대로 돌립니다(Shizuku).",
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

/** 변경 기록(앱 정보): 접었다 펴는 카드. 판마다 요약과 묶음별 바뀐 점. */
@Composable
internal fun ChangelogCard() {
    val c = LocalLiquid.current
    var open by remember { mutableStateOf(false) }
    GlassCard {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("변경 기록", fontWeight = FontWeight.SemiBold, color = c.ink)
                Text("${CHANGELOG.first().version} · FoldFit 1.0 이후 바뀐 점", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                CHANGELOG.forEach { r ->
                    Spacer(Modifier.height(Gap.Section))
                    Text("${r.version} · ${r.date}", fontWeight = FontWeight.Bold, color = c.ink)
                    Text(r.summary, color = c.muted, style = MaterialTheme.typography.bodySmall)
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
}

package com.labok.goat.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.liquid.LiquidShape

/**
 * 화면 간격 가이드라인. 모든 화면·시트의 세로 간격은 이 값만 쓴다(숫자 dp를 직접 쓰지 않는다, [GapGuideTest]가 검사).
 *
 * ```
 * ┌ 화면 가장자리 ── 좌우 16dp([featurePadding]·[SheetColumn])
 * │ ┌ 카드 ─ 안쪽 여백 [CardPadding] ──────────────┐
 * │ │ 제목                                          │
 * │ │ 설명(제목 바로 아래, 간격 없음)                │
 * │ │        ↕ [Item]   설명 → 조작(버튼·조절바·칩·그림) │
 * │ │ [버튼]                                        │
 * │ │        ↕ [Section] 카드 안의 다음 묶음         │
 * │ │ 제목 / 설명 / 스위치                            │
 * │ └──────────────────────────────────────────────┘
 * │        ↕ [Card]  카드와 카드, 목록 항목 사이, 섹션 제목 위
 * │ ┌ 카드 ...
 * ```
 *
 * 간격 규칙표(모든 화면·시트, [GapGuideTest]가 검사). 가까운 관계일수록 좁다:
 * | 관계                                                   | 간격 |
 * |--------------------------------------------------------|------|
 * | 제목 → 그 설명, 같은 설명의 문단과 문단                   | 없음(줄 간격만, 규칙 A) |
 * | 목록 항목(•) 사이, 배지·코드·알약 같은 메타 줄과 그 옆 글 | [Tight] |
 * | 글(제목·설명·힌트) ↔ 조작·그림(조절바·칩·버튼·라디오·입력칸·명령 상자·그림), 위아래 모두 | [Item] |
 * | 조작 ↔ 조작(세로로 쌓을 때. 가로로 나란한 버튼은 Row의 가로 간격) | [Item] |
 * | 카드와 카드, 목록 항목 사이, 섹션 제목 위                | [Card] |
 * | 한 카드 안에서 다음 설정 묶음(다음 제목)으로 넘어갈 때    | [Section] |
 * | 시트의 큰 제목 아래                                      | [Title] |
 * 규칙 A(줄 간격): 줄 높이는 LiquidTheme의 LiquidType이 정한다. 설명 줄 사이(약 3sp)가 제목↔설명(약 4.5sp)보다 좁다.
 * 규칙 B: 세로 간격은 Spacer(Gap.*)로만 둔다. 글·버튼에 padding(top = …)을 달아 간격을 만들지 않는다.
 * 규칙 C: 조절바는 높이 안에 위쪽 여백을 따로 두지 않는다(떠오르는 값 방울은 위 내용에 겹쳐 그린다).
 * 규칙 D: 누르는 줄 목록(정보 링크·라디오 등)은 줄마다 위아래 [RowPad]를 두고, 목록 전체에 [rowListEdges]를 달아
 *         맨 위·맨 아래 줄의 그 여백을 바깥과 겹친다. 카드 가장자리에서 첫 글자까지의 거리가 다른 카드와 같다.
 * - 시트의 큰 제목 아래는 [Title].
 * - 가로 간격(Row의 spacedBy, 아이콘과 글자 사이)은 이 규칙 밖이다.
 */
object Gap {
    /** 누르는 줄 하나의 위아래 여백(누르기 쉬운 높이). 목록 바깥 경계에서는 [rowListEdges]로 겹친다. */
    val RowPad = 10.dp

    /** 목록 항목(•) 사이, 배지·코드·알약 같은 메타 줄과 그 옆 글 사이. 글과 조작 사이에는 쓰지 않는다([Item]). */
    val Tight = 4.dp

    /** 글 ↔ 조작·그림(위아래 모두), 세로로 쌓은 조작 ↔ 조작. */
    val Item = 10.dp

    /** 카드와 카드 사이, 목록 항목 사이, 섹션 제목 위. */
    val Card = 12.dp

    /** 한 카드 안에서 다음 설정 묶음으로 넘어갈 때. */
    val Section = 14.dp

    /** 시트의 큰 제목 아래. */
    val Title = 16.dp

    /** 카드 안쪽 여백(네 변 모두). */
    val CardPadding = 16.dp

    /** 누름 물결이 카드 끝까지 차야 하는 카드(아코디언 머리 등): 카드는 여백 없이 두고, 안쪽 줄이 [CardPadding]을 갖는다. */
    val Flush = 0.dp
}

/**
 * 가로 간격 표준(11-05 소장님 "구성요소별 여백과 간격을 표준화해서 통일"). 세로 간격은 [Gap], 가로는 이 값만 쓴다([GapGuideTest]가 검사).
 * | 관계 | 값 |
 * |---|---|
 * | 화면·시트 좌우 가장자리 | [Edge] |
 * | 아이콘·아이콘 칸·물방울 점 ↔ 그 옆 글 | [IconText] |
 * | 글 ↔ 옆 배지·숫자·점·작은 표시, 이름 칸 ↔ 값 칸 | [Inline] |
 * | 숫자 ↔ 단위(dpi, %) | [Unit] |
 * | 가로로 나란한 버튼·카드·칩·항목 사이 | [Between] |
 * | 떨어진 덩어리 사이(범례 묶음, 큰 그림과 설명) | [Group] |
 */
object Space {
    val Edge = 16.dp
    val IconText = 12.dp
    val Inline = 8.dp
    val Unit = 4.dp
    val Between = 10.dp
    val Group = 16.dp
}

/** 구성요소 안쪽 여백 표준. 카드는 [Gap.CardPadding], 누르는 줄의 위아래는 [Gap.RowPad]. */
object Inset {
    /** 배지·태그·칭호·작은 상태 표시(글자 한 줄을 감싼 알약). 모양은 [PillShape]. */
    val Pill = PaddingValues(horizontal = 10.dp, vertical = 3.dp)

    /** 카드·목록 안에서 누르는 한 줄(앱 줄 등). */
    val Row = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

    /** 표·숫자 목록 한 줄의 위아래(이름 | 값 줄). */
    val TableRow = 4.dp

    /** 글자만 있는 작은 누르기 버튼('모두 보기' 등). */
    val TextButton = PaddingValues(horizontal = 8.dp, vertical = 6.dp)

    /** 경고·안내 상자처럼 카드 안에 넣는 테두리 상자. */
    val Box = 14.dp
}

/**
 * 모서리 둥글기 표준(11-05 소장님 "모서리 둥글기도 맞춰라").
 * | 구성요소 | 값 |
 * |---|---|
 * | 카드(GlassCard·목록 카드·요약 카드) | [Card] = LiquidShape.card |
 * | 카드 안의 상자(경고 상자·명령 상자·묶음 상자·색 견본) | [Box] |
 * | 누르는 줄·글자 버튼의 누름 범위 | [Row] |
 * | 버튼·칩·배지·막대 | 양끝이 둥근 [PillShape] |
 * | 아이콘 칸 | [Tile]의 Radius 값 |
 */
object Radius {
    val Card = LiquidShape.card
    val Box = 14.dp
    val Row = 12.dp
}

/** 알약 모양(배지·태그·칭호·버튼·막대). */
val PillShape = RoundedCornerShape(50)

/** 아이콘 칸 크기 표준. */
object Tile {
    /** 기능·항목 아이콘 칸(옅은 잉크 바탕 + 선 아이콘). */
    val Icon = 32.dp
    val IconRadius = 10.dp
    val IconGlyph = 18.dp

    /** 앱 아이콘(목록 줄). */
    val App = 38.dp
    val AppRadius = 11.dp

    /** 홈 즐겨찾기처럼 크게 보이는 기능 칸. */
    val Large = 40.dp
    val LargeRadius = 12.dp

    /** 앱 아이콘 모양 고르기처럼 크게 보여 주는 미리 보기. */
    val Preview = 56.dp
    val PreviewRadius = 16.dp

    /** 작은 앱 아이콘(순위 목록처럼 촘촘한 줄). */
    val AppSmall = 24.dp
    val AppSmallRadius = 7.dp
}

/**
 * 누르는 줄 목록의 바깥 경계 맞추기(규칙 5): 목록을 위아래로 [Gap.RowPad]만큼 겹쳐 놓는다. 줄을 누르는 범위는 그대로이고,
 * 첫 줄 글자와 마지막 줄 글자가 다른 요소와 같은 간격에 놓인다.
 */
fun Modifier.rowListEdges(): Modifier = layout { measurable, constraints ->
    val pad = Gap.RowPad.roundToPx()
    val p = measurable.measure(constraints)
    val h = (p.height - 2 * pad).coerceAtLeast(0)
    layout(p.width, h) { p.place(0, -pad) }
}

package com.labok.goat.ui.components

import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
 * 누르는 줄 목록의 바깥 경계 맞추기(규칙 5): 목록을 위아래로 [Gap.RowPad]만큼 겹쳐 놓는다. 줄을 누르는 범위는 그대로이고,
 * 첫 줄 글자와 마지막 줄 글자가 다른 요소와 같은 간격에 놓인다.
 */
fun Modifier.rowListEdges(): Modifier = layout { measurable, constraints ->
    val pad = Gap.RowPad.roundToPx()
    val p = measurable.measure(constraints)
    val h = (p.height - 2 * pad).coerceAtLeast(0)
    layout(p.width, h) { p.place(0, -pad) }
}

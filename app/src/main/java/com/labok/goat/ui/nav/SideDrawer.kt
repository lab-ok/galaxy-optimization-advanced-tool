package com.labok.goat.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labok.goat.ui.components.Gap
import com.labok.goat.ui.components.Space
import com.labok.goat.ui.liquid.LiquidTextField
import com.labok.goat.ui.liquid.LocalLiquid
import com.labok.goat.ui.liquid.LocalReduceMotion
import com.labok.goat.ui.liquid.Text
import com.labok.goat.ui.liquid.liquidPress
import com.labok.goat.ui.liquid.moveTo
import com.labok.goat.ui.liquid.rememberSoftBlob
import com.labok.goat.ui.text.Copy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 사이드바 항목 = 앱의 기능(화면) 단위. 기능 안의 세부 설정·도구는 각 화면의 점 세 개 메뉴에 둔다.
 * [icon]은 앱 그림과 같은 선 굵기로 직접 그린다.
 */
enum class NavItem(val label: String, val icon: NavIcon) {
    /** 메인 화면: 모든 기능의 지금 상태를 한눈에 보고 바로 들어간다. */
    HOME(Copy.FEATURE_HOME, NavIcon.HOME),

    /** 접고 펼 때도 두 화면의 표시 크기(DPI)를 일관되게 맞추는 기본 기능. */
    DPI_MATCH(Copy.FEATURE_DPI, NavIcon.FOLD),

    /** 앱마다 화면 크기를 따로 두는 기능(삼성 '앱 화면 크게/작게', Shizuku 필요). */
    APP_SIZE(Copy.FEATURE_APP_SIZE, NavIcon.APPS),

    /** 창·전환·애니메이터 배율(전역 설정). */
    ANIMATION(Copy.FEATURE_ANIMATION, NavIcon.SPEED),

    /** 평소에는 회전을 잠가 두고, 흔들면 폰이 놓인 방향으로 한 번 돌린다. */
    SHAKE_ROTATE(Copy.FEATURE_SHAKE_ROTATE, NavIcon.ROTATE),

    /** 국내 모델의 강제 촬영음을 끈다(Shizuku 셸로 숨은 시스템 설정을 바꾼다). */
    CAMERA_MUTE(Copy.FEATURE_CAMERA_MUTE, NavIcon.CAMERA),

    /** 쓰지 않는 기본 앱 끄기(Shizuku, 되돌릴 수 있음). */
    APP_CLEANUP(Copy.FEATURE_APP_CLEANUP, NavIcon.CLEAN),

    /** 화면 주사율의 위·아래 한계를 고정한다(Shizuku 셸). */
    REFRESH(Copy.FEATURE_REFRESH, NavIcon.REFRESH),

    /** 게임 중 성능을 낮추는 GOS를 끈다(Shizuku, 기본 앱 정리와 같은 끄기). */
    GOS(Copy.FEATURE_GOS, NavIcon.GAME),

    /** 잠자기 모드(Doze)를 꺼 알림이 늦게 오지 않게 한다(Shizuku, 재부팅 뒤 다시 끄기). */
    DOZE(Copy.FEATURE_DOZE, NavIcon.BELL),

    /** 해상도를 낮추고 밀도도 같은 비율로 줄인다(시스템 설정 권한, 폴드 제외). */
    RESOLUTION(Copy.FEATURE_RESOLUTION, NavIcon.PIXELS),

    /** 다크 모드 없는 앱도 시스템이 어둡게 그리게 한다(Shizuku setprop). */
    DARK(Copy.FEATURE_DARK, NavIcon.MOON),

    /** 비행기 모드를 켜도 고른 무선(와이파이·블루투스·NFC)은 그대로 둔다(시스템 설정 권한, 실험실에서 정식으로). */
    AIRPLANE(Copy.FEATURE_AIRPLANE, NavIcon.AIRPLANE),

    /** 화면을 돌리면 볼륨 버튼의 올리기·내리기를 화면 방향에 맞춘다(접근성 서비스, 1.16.1 실험실에서 정식으로). */
    VOLUME_ROTATE(Copy.FEATURE_VOLUME_ROTATE, NavIcon.VOLUME),

    /** 폴드8을 반쯤 접으면 플렉스 모드를 대신 켠다(Shizuku 셸, 힌지 각도, 1.29.0 실험실에서 정식으로). */
    AUTO_FLEX(Copy.FEATURE_AUTO_FLEX, NavIcon.FLEX),

    /** 상태 표시줄의 고른 아이콘을 숨긴다(시스템 설정 권한, 실험실에서 정식으로). */
    STATUS_ICONS(Copy.FEATURE_STATUS_ICONS, NavIcon.EYE_OFF),

    /** 실험실: 기능을 만들 때 쓰는 조사 도구(사이드바에는 lab 빌드에서만 보인다). */
    LAB_TOOLS(Copy.FEATURE_LAB_TOOLS, NavIcon.FLASK),

    /** 사용 통계: 접기·잠금 해제·화면·앱·알림·충전·볼륨 버튼 횟수를 기간별로(1.50.9 실험실에서 정식으로). 열어 본 횟수 기록이 이름에 묶여 있어 이름은 그대로 둔다. */
    LAB_STATS(Copy.FEATURE_LAB_STATS, NavIcon.STATS),

    /** 실험실: 앰비언트 모드(텐트 모드와 합침). 펼칠 때·세워 둘 때 안쪽 화면이 바깥 화면 색 간접등이 된다. */
    LAB_AMBIENT(Copy.FEATURE_LAB_AMBIENT, NavIcon.GLOW),

    /** 실험실: 삼성 키보드 마이크를 위스퍼 음성 입력기로 넘기기와 그 조사(11-08 소장님). */
    LAB_VOICE(Copy.FEATURE_LAB_VOICE, NavIcon.VOLUME),

    /** 클립보드 공유: 복사한 글·이미지·파일을 파일로 공유한다(1.49.0 실험실에서 정식으로, 'TXT로 공유'에서 이름 바꿈). 열어 본 횟수 기록이 이름에 묶여 있어 이름은 그대로 둔다. */
    LAB_NOTE(Copy.FEATURE_LAB_NOTE, NavIcon.NOTE),

    /** 앱 설정: 앱 정보·업데이트·기록 파일·UI 탄성(사이드바 맨 아래에서 연다, 홈 카드에는 없다). */
    SETTINGS(Copy.FEATURE_SETTINGS, NavIcon.GEAR)
    ;

    companion object {
        /** 실험실 묶음 항목(홈 타일에는 넣지 않는다). */
        val LAB_ITEMS = setOf(LAB_AMBIENT, LAB_VOICE, LAB_TOOLS)
    }
}


data class NavSection(val title: String, val items: List<NavItem>)

/**
 * 사이드바 내용(리퀴드): 열릴 때 로고가 방울처럼 톡 튀어나오고 항목이 차례로 미끄러져 들어온다.
 * 고른 항목 아래의 잉크 방울은 항목을 바꾸면 머리(빠른 스프링)와 꼬리(느린 스프링)가 goo로 이어져
 * 늘어났다가 합쳐지며 옮겨 간다(LiquidChips와 같은 원리, 세로 방향).
 * @param visible 사이드바가 열려 있는지. 열릴 때마다 등장 애니메이션을 다시 한다.
 */
@Composable
fun SideDrawerContent(
    sections: List<NavSection>,
    current: NavItem?,
    visible: Boolean,
    /** 고른 항목과 그 줄 가운데의 화면 좌표(새 화면이 거기서 번진다). */
    onSelect: (NavItem, Offset?) -> Unit,
    onAbout: () -> Unit,
    /** 위에 덮이는 서랍일 때: 살짝 비치는 유리처럼 뒤(흐려진 본문)가 은은하게 보인다. */
    glass: Boolean = false,
    /**
     * 고른 항목으로 방울이 옮겨 가는 애니메이션을 할지. 늘 펼쳐진 사이드바(태블릿·폴드 내부)에서만 한다.
     * 덮이는 서랍은 고르자마자 닫히고 화면이 바뀌어 볼 틈이 없으므로, 방울을 그 자리로 바로 옮긴다.
     */
    animateSelection: Boolean = true
) {
    val c = LocalLiquid.current
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    val items = sections.flatMap { it.items }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(visible) { if (!visible) query = "" }

    // 등장: 항목이 차례로(머리는 DrawerHeader가 물방울로 맺힌다).
    // 항목마다 55ms씩 늦게 띄우던 것은 메뉴가 늘며 1.3초 넘게 걸려 느렸다(11-01 소장님): 전체를 [ENTER_MAX_MS] 안에 끝내고,
    // 한 항목이 [ENTER_FADE]칸에 걸쳐 나타나게 해 간격이 좁아도 툭 튀지 않게 한다
    val enterEnd = items.size + sections.size + 2f + ENTER_FADE
    val enter = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (!visible) {
            enter.snapTo(0f)
            return@LaunchedEffect
        }
        if (reduce) {
            enter.snapTo(enterEnd)
            return@LaunchedEffect
        }
        enter.animateTo(enterEnd, tween(minOf(120 + 20 * (items.size + sections.size + 2), ENTER_MAX_MS), easing = FastOutSlowInEasing))
    }
    // 열려 있는 동안 항목이 늘면(염소를 열 번 눌러 실험실이 생길 때) 새 항목도 마저 나타나게 끝값을 늘린다.
    // 늘리지 않으면 새 항목은 등장 값이 0에 머물러 투명한 채로 남는다
    LaunchedEffect(items.size, sections.size) {
        val end = enterEnd
        if (visible && enter.value > 0f && enter.targetValue < end) enter.animateTo(end, tween(if (reduce) 0 else 220, easing = LinearEasing))
    }

    // 고른 항목 방울: 각 항목의 위치를 재고, 머리·꼬리 스프링으로 옮긴다
    val tops = remember { mutableStateMapOf<NavItem, Float>() }
    val centers = remember { HashMap<NavItem, Offset>() }
    var rowH by remember { mutableFloatStateOf(0f) }
    val target = current?.let { tops[it] }
    // 고른 항목 아래 물방울(연체 물리). 항목을 바꾸면 그 자리로 끌려가며 휘었다가 표면장력으로 둥글게 돌아온다.
    val blob = rememberSoftBlob()
    var boxW by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target, rowH, boxW) {
        val y = target ?: return@LaunchedEffect
        if (rowH > 0f && boxW > 0f) blob.moveTo(boxW / 2, y + rowH / 2, boxW, rowH, animate = !reduce && animateSelection)
    }

    Box(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
            .background(if (glass) c.bg.copy(alpha = GLASS_ALPHA) else c.bg)
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(DRAWER_SEARCH_PAD)
        ) {
            DrawerHeader(version, visible)
            // 설정 검색: 기능 이름이나 기능 안의 설정 이름으로 찾아 바로 연다(초성으로도 찾는다)
            Spacer(Modifier.height(Gap.Item))
            LiquidTextField(
                query,
                { query = it },
                placeholder = Copy.DRAWER_SEARCH,
                modifier = Modifier.fillMaxWidth(),
                leading = { Icon(Icons.Outlined.Search, null, tint = c.muted) }
            )
            if (query.isNotBlank()) {
                val hits = remember(query, items) { SettingsSearch.search(query, items + NavItem.SETTINGS) }
                Spacer(Modifier.height(Gap.Item))
                if (hits.isEmpty()) {
                    Text(Copy.DRAWER_SEARCH_NONE, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = DRAWER_EDGE))
                }
                hits.forEach { hit ->
                    SearchRow(hit) {
                        query = ""
                        onSelect(hit.item, null)
                    }
                }
            } else {
                Box(Modifier.onSizeChanged { boxW = it.width.toFloat() }) {
                    // 방울 층(항목 뒤): 고른 항목을 따라다니는 연체 물방울
                    val path = remember { Path() }
                    Canvas(Modifier.matchParentSize()) {
                        blob.frame // 프레임마다 다시 그리기
                        if (blob.blob.ready) drawPath(blob.blob.path(path), c.ink)
                    }
                    Column {
                        var index = 0
                        sections.forEach { section ->
                            Spacer(Modifier.height(Gap.Section))
                            val titleOrder = index
                            // 제목 없는 묶음(맨 위 홈)은 제목 줄을 그리지 않는다
                            if (section.title.isNotEmpty()) {
                                Text(
                                    section.title,
                                    color = c.muted,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = DRAWER_EDGE, bottom = 6.dp).graphicsLayer { alpha = ((enter.value - titleOrder) / ENTER_FADE).coerceIn(0f, 1f) }
                                )
                            }
                            section.items.forEach { item ->
                                val order = ++index
                                DrawerRow(
                                    item,
                                    item == current,
                                    cover = cover@{
                                        val top = tops[item] ?: return@cover if (item == current) 1f else 0f
                                        blob.frame
                                        if (!blob.blob.ready || rowH <= 0f) {
                                            (if (item == current) 1f else 0f)
                                        } else {
                                            (1f - abs(blob.blob.cy - rowH / 2 - top) / rowH).coerceIn(0f, 1f)
                                        }
                                    },
                                    appear = { ((enter.value - order) / ENTER_FADE).coerceIn(0f, 1f) },
                                    modifier = Modifier.onGloballyPositioned {
                                        tops[item] = it.positionInParent().y
                                        rowH = it.size.height.toFloat()
                                        centers[item] = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                                    }
                                ) { onSelect(item, centers[item]) }
                            }
                            index++
                        }
                        // 맨 아래: 앱 설정. 기능 묶음과 선으로 나누되, 고르면 다른 항목처럼 잉크 방울이 옮겨 와 덮는다.
                        Spacer(Modifier.height(Gap.Section))
                        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = DRAWER_EDGE).background(c.line))
                        Spacer(Modifier.height(Gap.Item))
                        val settingsOrder = ++index
                        DrawerRow(
                            NavItem.SETTINGS,
                            current == NavItem.SETTINGS,
                            cover = cover@{
                                val top = tops[NavItem.SETTINGS] ?: return@cover if (current == NavItem.SETTINGS) 1f else 0f
                                blob.frame
                                if (!blob.blob.ready || rowH <= 0f) {
                                    (if (current == NavItem.SETTINGS) 1f else 0f)
                                } else {
                                    (1f - abs(blob.blob.cy - rowH / 2 - top) / rowH).coerceIn(0f, 1f)
                                }
                            },
                            appear = { ((enter.value - settingsOrder) / ENTER_FADE).coerceIn(0f, 1f) },
                            modifier = Modifier.onGloballyPositioned { tops[NavItem.SETTINGS] = it.positionInParent().y },
                            onClick = { onSelect(NavItem.SETTINGS, null) }
                        )
                    }
                }
            }
        }
    }
}

/** 검색 결과 한 줄: 기능 아이콘, 찾은 설정 이름, 그 아래 어느 기능에 있는지. */
@Composable
private fun SearchRow(hit: SettingsSearch.Hit, onClick: () -> Unit) {
    val c = LocalLiquid.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .liquidPress(interaction, sx = 1.02f, sy = 0.95f)
            .clip(RoundedCornerShape(DRAWER_ROW_RADIUS))
            .clickable(interaction, null, onClick = onClick)
            .padding(start = DRAWER_EDGE, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavGlyph(hit.item.icon, c.ink)
        Spacer(Modifier.width(Space.IconText))
        Column {
            Text(hit.title, color = c.ink, fontWeight = FontWeight.Medium)
            if (hit.title != hit.item.label) Text(hit.item.label, color = c.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DrawerRow(
    item: NavItem,
    selected: Boolean,
    cover: () -> Float,
    appear: () -> Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = LocalLiquid.current
    val interaction = remember { MutableInteractionSource() }
    // 글자·아이콘 색은 잉크 방울이 이 항목을 덮은 만큼 배경색으로 바뀐다(방울이 도착할 때 반전).
    val fg = lerp(c.ink, c.onInk, cover())
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                val k = appear()
                alpha = k
                translationX = (1f - k) * -18.dp.toPx()
            }
            .liquidPress(interaction, sx = 1.02f, sy = 0.95f)
            .clip(RoundedCornerShape(DRAWER_ROW_RADIUS))
            .clickable(interaction, null, onClick = onClick)
            // 선택 방울의 둥근 끝(반지름 24dp) 안쪽 깊숙이 아이콘을 둬 검은 경계와 붙어 보이지 않게 한다.
            .padding(start = DRAWER_EDGE, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavGlyph(item.icon, fg)
        Spacer(Modifier.width(Space.IconText))
        Text(item.label, color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        // 목적별 칸에 섞어 둔 실험실 기능은 이름 옆에 작게 '실험'(11-03 카테고리 재편)
        if (item in NavItem.LAB_ITEMS && item != NavItem.LAB_TOOLS) {
            Spacer(Modifier.width(Space.Inline))
            Text(Copy.NAV_LAB_TAG, color = fg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        // 새 기능이 들어온 항목은 이름 옆에 점을 찍는다(그 화면을 보고 나가면 사라진다)
        if (NewBadge.navNew(NEW_BADGE_NAV[item])) {
            Spacer(Modifier.width(Space.Inline))
            NewDot()
        }
    }
}

/** 덮이는 서랍·시트의 불투명도: 흐려진 뒤 화면이 살짝 비친다. */
internal const val GLASS_ALPHA = 0.84f

/** 사이드 메뉴 항목 등장 전체 시간 상한(ms)과 한 항목이 나타나는 폭(항목 칸 수). */
private const val ENTER_MAX_MS = 380
private const val ENTER_FADE = 3f

/** 사이드바 안쪽 가장자리: 선택 방울의 둥근 끝(반지름 24dp) 안쪽에 아이콘을 두는 거리. 제목·구분선·검색 결과도 같은 선에 맞춘다. */
private val DRAWER_EDGE = 22.dp

/** 사이드바 검색칸 안쪽 여백. */
private val DRAWER_SEARCH_PAD = PaddingValues(horizontal = 14.dp, vertical = 18.dp)

/** 사이드바 줄 모서리: 선택 방울(높이 48dp)과 같은 반지름이라 누름 범위가 방울 모양과 겹친다. */
private val DRAWER_ROW_RADIUS = 24.dp

/** 사이드바 항목마다 새 기능 점을 띄울 키([NewBadge]). 새 기능을 넣을 때 [NewBadge.ALL]과 함께 더한다. */
val NEW_BADGE_NAV: Map<NavItem, List<String>> = mapOf(
    NavItem.STATUS_ICONS to listOf(NewBadge.STATUS_BAR_HIDE),
    NavItem.AUTO_FLEX to listOf(NewBadge.AUTO_FLEX),
    NavItem.SETTINGS to listOf(NewBadge.INK_TONES),
    NavItem.VOLUME_ROTATE to listOf(NewBadge.VOLUME_ROTATE),
    NavItem.LAB_TOOLS to listOf(NewBadge.SHARE_PROBE),
    NavItem.LAB_STATS to listOf(NewBadge.USAGE_STATS)
)

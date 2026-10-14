#!/usr/bin/env bash
# 화면 전수 촬영: 연결된 기기(에뮬레이터)에 설치된 GOAT의 모든 기능 화면과 시트를
# 화면 폭 × 테마 × 글자 크기 조합으로 찍는다. 화면마다 여러 쪽을 아래로 넘기며 찍고, 조합별 한 장짜리 모음도 만든다.
#
# 사용법: tools/ui-sweep.sh [출력 폴더]
#   ADB      adb 명령(기본: adb). 예) ADB="adb -s 127.0.0.1:5556"
#   COMBOS   "폭:테마:글자" 목록(기본: "wide:light:1.0 wide:dark:1.0 narrow:light:1.3 narrow:dark:1.3")
#            폭은 wide(기기 기본 크기) 또는 narrow(820x2000, 약 365dp), 테마는 light/dark, 글자는 font_scale 값
#   PAGES    화면마다 찍을 쪽 수(기본 3), SHEET_PAGES 시트마다 쪽 수(기본 2)
#   ONLY     screens 또는 sheets만 찍기(기본: 둘 다)
#
# 촬영이 끝나면 기기의 화면 크기·글자 크기·다크 모드를 원래대로 되돌린다.
# GOAT를 화면·시트별로 바로 여는 데 MainActivity의 screen/sheet 값을 쓴다(보여 주기만 하고 아무것도 실행하지 않는다).
set -euo pipefail

OUT=${1:-build/ui-sweep}
ADB=${ADB:-adb}
COMBOS=${COMBOS:-"wide:light:1.0 wide:dark:1.0 narrow:light:1.3 narrow:dark:1.3"}
PAGES=${PAGES:-3}
SHEET_PAGES=${SHEET_PAGES:-2}
ONLY=${ONLY:-}
PKG=com.labok.goat
ACT=$PKG/.ui.MainActivity

SCREENS="HOME DPI_MATCH APP_SIZE RESOLUTION REFRESH ANIMATION DARK SHAKE_ROTATE CAMERA_MUTE APP_CLEANUP GOS DOZE SETTINGS LAB_AIRPLANE LAB_ICONS LAB_SCREEN LAB_FLEX LAB_PROBE"
SHEETS="HELP GUIDE TEST ADVANCED RESET ABOUT APP_HELP APP_GUIDE APP_RESET SHAKE_HELP SHAKE_CALIBRATE CAMERA_HELP ANIM_HELP BLOAT_HELP REFRESH_HELP GOS_HELP DARK_HELP RES_HELP DOZE_HELP CHANGELOG PERMISSIONS"

adbs() { $ADB shell "$@" | tr -d '\r'; }

# 원래 상태를 기억해 두고 끝나면(중간에 멈춰도) 되돌린다
ORIG_FONT=$(adbs settings get system font_scale)
ORIG_NIGHT=$(adbs cmd uimode night | sed 's/.*: //')
restore() {
  $ADB shell wm size reset >/dev/null 2>&1 || true
  $ADB shell settings put system font_scale "$ORIG_FONT" >/dev/null 2>&1 || true
  $ADB shell cmd uimode night "$ORIG_NIGHT" >/dev/null 2>&1 || true
}
trap restore EXIT

open_goat() { # 화면 [시트]
  local extra="--es screen $1"
  if [ -n "${2:-}" ]; then extra="$extra --es sheet $2"; fi
  $ADB shell am start -S -n "$ACT" $extra >/dev/null
  sleep 3.5
}

shoot_pages() { # 이름 쪽수 출력폴더
  for p in $(seq 1 "$2"); do
    $ADB exec-out screencap -p > "$3/$1_$p.png"
    if [ "$p" -lt "$2" ]; then
      $ADB shell input swipe 400 1500 400 600 350
      sleep 1.2
    fi
  done
}

for combo in $COMBOS; do
  IFS=: read -r width theme font <<<"$combo"
  dir="$OUT/$width-$theme-$font"
  mkdir -p "$dir"
  if [ "$width" = narrow ]; then $ADB shell wm size 820x2000; else $ADB shell wm size reset; fi
  $ADB shell settings put system font_scale "$font"
  $ADB shell cmd uimode night "$([ "$theme" = dark ] && echo yes || echo no)" >/dev/null
  sleep 2
  if [ "$ONLY" != sheets ]; then
    for s in $SCREENS; do open_goat "$s"; shoot_pages "$s" "$PAGES" "$dir"; done
  fi
  if [ "$ONLY" != screens ]; then
    for sh in $SHEETS; do open_goat HOME "$sh"; shoot_pages "sheet_$sh" "$SHEET_PAGES" "$dir"; done
  fi
  echo "찍음: $dir ($(ls "$dir" | wc -l)장)"
done
$ADB shell am force-stop "$PKG"

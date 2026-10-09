#!/usr/bin/env bash
# APK 설치 → WRITE_SECURE_SETTINGS 부여 → 앱 실행 → 권한 확인.
# 화면 density는 바꾸지 않는다. 설치 전 현재 density를 logs/에 기록만 한다.
#
# 사용법: ./install-and-grant.sh [apk경로]   (기본: debug APK)
#         여러 단말이 연결돼 있으면 ANDROID_SERIAL=<serial> 로 지정한다.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PKG=com.local.folddpifix
PERM=android.permission.WRITE_SECURE_SETTINGS
APK="${1:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"

die() { echo "ERROR: $*" >&2; exit 1; }

# adb 찾기: PATH → ~/tools/android/adb(Ubuntu ARM64 adb 래퍼)
if command -v adb >/dev/null 2>&1; then
  ADB=adb
elif [ -x "$HOME/tools/android/adb" ]; then
  ADB="$HOME/tools/android/adb"
else
  die "adb가 없습니다. README의 'adb 준비'를 보십시오."
fi

[ -f "$APK" ] || die "APK가 없습니다: $APK (먼저 ./build-apk.sh)"

echo "[1/5] adb 연결 확인"
"$ADB" start-server >/dev/null
mapfile -t DEVICES < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
mapfile -t BAD < <("$ADB" devices | awk 'NR>1 && NF>=2 && $2!="device" {s=$1; $1=""; print s" ("substr($0,2)")"}')
[ ${#BAD[@]} -eq 0 ] || echo "  사용할 수 없는 장치: ${BAD[*]} — unauthorized면 폰에서 USB 디버깅 허용을 누르십시오."
if [ -n "${ANDROID_SERIAL:-}" ]; then
  printf '%s\n' "${DEVICES[@]}" | grep -qx "$ANDROID_SERIAL" || die "ANDROID_SERIAL=$ANDROID_SERIAL 장치가 연결돼 있지 않습니다"
else
  [ ${#DEVICES[@]} -ge 1 ] || die "연결된 단말이 없습니다 (adb devices)"
  [ ${#DEVICES[@]} -eq 1 ] || die "단말이 여러 대입니다: ${DEVICES[*]} — ANDROID_SERIAL을 지정하십시오"
  export ANDROID_SERIAL="${DEVICES[0]}"
fi
MODEL="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
MANU="$("$ADB" shell getprop ro.product.manufacturer | tr -d '\r')"
SDK="$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "  단말: $ANDROID_SERIAL · $MANU $MODEL · SDK $SDK"
case "$MODEL" in SM-F9*) ;; *) echo "  WARN: Galaxy Z Fold(SM-F9xx)가 아닙니다. 계속 진행합니다." ;; esac

mkdir -p "$ROOT/logs"
BEFORE="$ROOT/logs/density-before-$(date +%Y%m%d-%H%M%S).txt"
"$ADB" shell wm density | tr -d '\r' | tee "$BEFORE"
echo "  설치 전 density를 기록했습니다: $BEFORE"

echo "[2/5] APK 설치"
"$ADB" install -r "$APK" || die "설치 실패. 서명이 다른 기존 앱이 있으면: $ADB uninstall $PKG"

echo "[3/5] $PERM 부여"
"$ADB" shell pm grant "$PKG" "$PERM" || die "pm grant 실패. 개발자 옵션의 USB 디버깅이 켜져 있는지 확인하십시오."

echo "[4/5] 앱 실행"
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null || die "앱 실행 실패"

echo "[5/5] 권한 확인"
if "$ADB" shell dumpsys package "$PKG" | tr -d '\r' | grep -q "$PERM: granted=true"; then
  echo "  OK: $PERM granted=true"
else
  die "$PERM 이 부여되지 않았습니다"
fi
echo
echo "완료. 앱에서 목표 DPI를 입력하고 [지금 적용]을 누르십시오."
echo "문제가 생기면: $ADB shell wm density reset"

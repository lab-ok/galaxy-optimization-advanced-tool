#!/usr/bin/env bash
# Fold DPI Fix APK 빌드.
#
# 기본(native): ARM64 Linux에서 바로 빌드한다. sudo 없이 $TOOLS 아래에만 도구를 설치한다.
#   - JDK 17 (Temurin aarch64)
#   - Android SDK platform/build-tools (Java 부분만 사용, AGP가 자동 설치)
#   - aapt2: Google 배포본은 x86_64 전용이라 ARM64 정적 빌드(lzhiyong/android-sdk-tools)로 대체
# --docker: x86_64 호스트(또는 amd64 에뮬레이션이 설정된 호스트)에서 Dockerfile.android-build로 빌드한다.
#
# 사용법: ./build-apk.sh [--release|--lab] [--docker] [--skip-tests]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="${FOLDDPI_TOOLS:-$HOME/tools/android}"
VARIANT=debug
MODE=native
RUN_TESTS=1
for a in "$@"; do
  case "$a" in
    --release) VARIANT=release ;;
    --lab) VARIANT=lab ;;
    --docker) MODE=docker ;;
    --skip-tests) RUN_TESTS=0 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "알 수 없는 옵션: $a" >&2; exit 2 ;;
  esac
done
TASK="assemble${VARIANT^}"
APK="$ROOT/app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"

die() { echo "ERROR: $*" >&2; exit 1; }

JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/aarch64/jdk/hotspot/normal/eclipse"
ARM64_TOOLS_URL="https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-aarch64.zip"
# repository2-3.xml 의 android-sdk-license 본문 SHA1. 라이선스 동의를 기록해야 AGP가 SDK 구성요소를 받는다.
SDK_LICENSE_HASHES=("9002c006f4b8d9a16e715a9fa4df30ddb8abf9d9" "24333f8a63b6825ea9c5514f83c2829b004d1fee")

setup_native() {
  mkdir -p "$TOOLS/dl"
  if [ ! -x "$TOOLS/jdk17/bin/java" ]; then
    echo "[setup] JDK 17 설치 → $TOOLS/jdk17"
    curl -fL -o "$TOOLS/dl/jdk17.tgz" "$JDK_URL"
    mkdir -p "$TOOLS/jdk17" && tar xzf "$TOOLS/dl/jdk17.tgz" -C "$TOOLS/jdk17" --strip-components=1
  fi
  if [ ! -x "$TOOLS/arm64-tools/build-tools/aapt2" ]; then
    echo "[setup] ARM64 aapt2 설치 → $TOOLS/arm64-tools"
    curl -fL -o "$TOOLS/dl/arm64-tools.zip" "$ARM64_TOOLS_URL"
    mkdir -p "$TOOLS/arm64-tools" && unzip -q -o "$TOOLS/dl/arm64-tools.zip" -d "$TOOLS/arm64-tools"
  fi
  mkdir -p "$TOOLS/sdk/licenses"
  if [ ! -s "$TOOLS/sdk/licenses/android-sdk-license" ]; then
    printf '\n%s\n' "${SDK_LICENSE_HASHES[@]}" > "$TOOLS/sdk/licenses/android-sdk-license"
  fi
  "$TOOLS/arm64-tools/build-tools/aapt2" version >/dev/null 2>&1 \
    || die "aapt2가 이 호스트에서 실행되지 않습니다: $TOOLS/arm64-tools/build-tools/aapt2"
}

build_native() {
  [ "$(uname -m)" = "aarch64" ] || echo "WARN: aarch64가 아닌 호스트($(uname -m))입니다. x86_64라면 --docker 또는 Google aapt2를 쓰십시오."
  setup_native
  export JAVA_HOME="$TOOLS/jdk17"
  export ANDROID_HOME="$TOOLS/sdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "sdk.dir=$ANDROID_HOME" > "$ROOT/local.properties"
  local args=(--no-daemon "-Pandroid.aapt2FromMavenOverride=$TOOLS/arm64-tools/build-tools/aapt2")
  cd "$ROOT"
  if [ "$RUN_TESTS" = 1 ]; then
    ./gradlew "${args[@]}" testDebugUnitTest
  fi
  ./gradlew "${args[@]}" "$TASK"
}

build_docker() {
  command -v docker >/dev/null || die "docker가 없습니다"
  docker run --rm --platform linux/amd64 alpine true >/dev/null 2>&1 \
    || die "이 호스트에서 linux/amd64 컨테이너를 실행할 수 없습니다(exec format error). ARM64에서는 기본(native) 모드를 쓰십시오."
  docker build --platform linux/amd64 -f "$ROOT/Dockerfile.android-build" -t folddpifix-build "$ROOT"
  local tasks="$TASK"
  [ "$RUN_TESTS" = 1 ] && tasks="testDebugUnitTest $TASK"
  docker run --rm --platform linux/amd64 -u "$(id -u):$(id -g)" -e HOME=/tmp \
    -v "$ROOT:/work" -w /work folddpifix-build bash -c "./gradlew --no-daemon $tasks"
}

if [ "$MODE" = docker ]; then build_docker; else build_native; fi

[ -f "$APK" ] || die "APK가 생성되지 않았습니다: $APK"
"$TOOLS/arm64-tools/build-tools/aapt2" dump badging "$APK" 2>/dev/null | head -1 || true
echo
echo "APK: $APK"

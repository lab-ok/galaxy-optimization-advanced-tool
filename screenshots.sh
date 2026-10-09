#!/usr/bin/env bash
# 화면 캡처 검증(Robolectric + Roborazzi). 결과: app/build/outputs/roborazzi 폴더의 PNG 파일.
#
# Robolectric 네이티브 그래픽은 linux-x86_64용만 배포된다. ARM64 Linux에서는 sudo 없이
# qemu-user로 x86_64 JDK를 돌린다(JIT가 qemu에서 죽어 -Xint, 테스트 1개당 수 분).
#   준비(최초 1회, $TOOLS/x86):
#     apt-get download qemu-user-static → dpkg -x → qemu/
#     docker create --platform linux/amd64 eclipse-temurin:17-jdk → docker export | tar -x → rootfs/
#     rootfs/etc/resolv.conf ← /etc/resolv.conf
#     jdk/bin/java ← qemu-x86_64-static -L rootfs rootfs/opt/java/openjdk/bin/java -Duser.home=$HOME -Xint ... "$@"
#   Robolectric 런타임 jar(android-all-instrumented 15-robolectric-12650502-i7)는 ~/.m2에 미리 받아 둔다.
# x86_64 호스트면 JAVA_X86 을 일반 JDK의 java 로 지정하면 된다.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="${FOLDDPI_TOOLS:-$HOME/tools/android}"
JAVA_X86="${JAVA_X86:-$TOOLS/x86/jdk/bin/java}"
export JAVA_HOME="$TOOLS/jdk17" ANDROID_HOME="$TOOLS/sdk"
cd "$ROOT"
./gradlew --no-daemon -Pandroid.aapt2FromMavenOverride="$TOOLS/arm64-tools/build-tools/aapt2" \
  -Pscreenshot="$JAVA_X86" testDebugUnitTest --tests "com.local.folddpifix.screenshot.*" "$@"
ls -1 app/build/outputs/roborazzi

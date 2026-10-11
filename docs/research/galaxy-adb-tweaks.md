# Galaxy adb/Shizuku 꿀팁 조사 (GOAT 앱용) — 2026-10-11

아래는 WRITE_SECURE_SETTINGS 보유 앱 또는 Shizuku(shell uid)로 원탭 제공 가능한 추가 기능 조사.

---

## 가장 많이 쓰고 만족도 높은 TOP 10 (커뮤니티 언급 빈도/반응 기준)

순위는 디시인사이드 갤럭시 마이너 갤러리·클리앙·삼성 멤버스·XDA에서 확인한 조회수·추천수·반복 질문 빈도로 추정한 **상대 순위**이며, 정량 투표 집계는 아님(사실/추정 구분).

| 순위 | 항목 | 근거(사실) |
|---|---|---|
| 1 | **블로트웨어 비활성화/삭제** (`pm disable-user --user 0`, `pm uninstall --user 0`) | 디시인사이드 "S24 출시기념 ADB 명령어 비활성화 리스트" 글 — 추천 43, 댓글 24, **조회 35,916** (2024-02-22) [[dcinside]](https://gall.dcinside.com/mgallery/board/view/?id=galaxy&no=1290115). 신기종 출시마다 반복 작성되는 단골 글 |
| 2 | **카메라 셔터음 무음** (`csc_pref_camera_forced_shuttersound_key`) | 한국/일본 전용 질문으로 Samsung Members·네이버·클리앙·X(트위터)·XDA에 지속 반복 등장 [[Samsung Members]](https://r1.community.samsung.com/t5/%EA%B0%A4%EB%9F%AD%EC%8B%9C-s/%EB%AC%B4%EC%9D%8C-%EC%84%A4%EC%A0%95-%ED%9E%98%EB%93%9C%EC%8B%9C%EC%A3%A0/td-p/37743193) [[X/Shizuku 사례]](https://x.com/s32x_arch/status/2029137147228545279) |
| 3 | **애니메이션 배율 축소/커스텀** (window/transition/animator_duration_scale) | 클리앙 "애니메이션 속도를 원하는대로" 글 — 조회 11,474, 추천 4, 댓글 14, 사용자들이 0.5~1.25x 사이 선호도 토론 [[클리앙]](https://www.clien.net/service/board/cm_andro/17948406). XDA에도 "Animations set scale set to .25" 등 반복 스레드 [[XDA]](https://xdaforums.com/t/animations-set-scale-set-to-25.4365695/) |
| 4 | **최대/최소 주사율 강제** (`peak_refresh_rate`/`min_refresh_rate` 120 고정) | DroidWin 등 다수 가이드에서 "120Hz 강제"로 반복 소개, 배터리/화면전환 민원과 함께 자주 언급 [[DroidWin]](https://droidwin.com/force-120hz-refresh-rate-in-any-android-via-adb-commands/) |
| 5 | **GOS(Game Optimizing Service) 비활성화** | Samsung Members 게이밍허브 스레드 조회 6,767 + 4,795(관련 글) — "GPU 100%→70%, 발열 논쟁" 식의 찬반 토론이 반복적으로 벌어짐 [[Samsung Members]](https://r2.community.samsung.com/t5/Gaming-Hub/Disable-game-optimizing-service/td-p/13299427) |
| 6 | **배터리 보호(충전 상한) 설정** | One UI 기본 제공 토글(85%/80~95%)이라 adb 수요는 "설정 메뉴에 없는 구형 기기"나 세분화 희망자 중심, 커뮤니티 질문 빈도 높음 [[Samsung]](https://www.samsung.com/ae/support/mobile-devices/what-are-the-specific-differences-of-the-battery-protection-options/) — *추정: adb 전용 수요는 중간 수준* |
| 7 | **상태표시줄 아이콘 숨기기** (`icon_blacklist`) | XDA에 "[GUIDE] Hiding status bar icons" 등 장기 지속 스레드 다수, Good Lock(QuickStar)으로 대체 가능해 adb 수요는 꾸준하지만 TOP5보다 낮음 [[XDA]](https://xdaforums.com/t/guide-hiding-status-bar-icons.3853258/) |
| 8 | **화면 해상도/밀도 변경** (`wm size`/`wm density`, 성능·배터리용) | XDA "Lower your resolution... (for gamers)" 등 게이머 대상 반복 스레드 [[XDA]](https://xdaforums.com/t/lower-your-resolution-the-proper-way-for-gamers-with-adb.4083283/) |
| 9 | **Private DNS 설정** (`private_dns_mode`/`private_dns_specifier`) | Cloudflare 공식 문서 기반 범용 안드로이드 팁, 갤럭시 특화 커뮤니티보다는 일반 보안 커뮤니티에서 더 자주 언급 [[Cloudflare]](https://developers.cloudflare.com/1.1.1.1/setup/android/) |
| 10 | **다크모드 강제(앱별 오버라이드)** | 9to5Google(DarQ), gHacks 등 범용 안드로이드 매체에서 반복 소개되나 갤럭시 고유 수요 신호는 약함 [[9to5Google]](https://9to5google.com/2020/02/17/how-to-darq-force-dark-android-10-without-root/) |

메모: Reddit(r/GalaxyFold, r/samsung, r/galaxys24 등)의 직접 투표수는 검색 API로 확인 불가(검색 결과가 Samsung Members/XDA/dcinside로 쏠림). 위 순위는 **확인 가능한 조회수·추천수·반복 등장 빈도** 기준이며, Reddit 자체 upvote 수치는 원문 접속 제한으로 추정치로만 반영.

---

## Part A — 카메라 셔터음 무음 (`csc_pref_camera_forced_shuttersound_key`)

### 명령어 (사실, 다수 1차/커뮤니티 소스 일치)
```
adb shell settings put system csc_pref_camera_forced_shuttersound_key 0   # 무음
adb shell settings put system csc_pref_camera_forced_shuttersound_key 1   # 강제 유음(기존값 복귀)
adb shell settings delete system csc_pref_camera_forced_shuttersound_key  # 키 삭제(펌웨어 기본값으로)
```
- 0 = 셔터음 끄기, 1 = 셔터음 강제 켜기. [[Samsung Members 가이드]](https://r1.community.samsung.com/t5/%ED%8C%81/adb%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%9C-%EC%B9%B4%EB%A9%94%EB%9D%BC-%EB%AC%B4%EC%9D%8C-%EC%84%A4%EC%A0%95-%EB%B0%A9%EB%B2%95-%EC%BB%B4%ED%93%A8%ED%84%B0x/td-p/23261439)

### 적용 대상 One UI 버전
- Galaxy S23 Ultra, One UI 6(Android 14) 대 기준 사례가 가장 많이 확인됨 [[note.com]](https://note.com/nadro/n/n10b6f4138ea6?hl=en).
- 2026년 현재도 동일 키 이름으로 통용 중(별도 키 변경 보고 없음) — S25/One UI 8.5 관련 스레드에서도 동일 명령 사용 확인 [[Samsung Community US]](https://us.community.samsung.com/t5/Galaxy-S25/Hate-the-camera-shutter-sound-with-the-One-UI-8-5/m-p/3583461). 단, One UI 8.5 업데이트에서 Samsung이 **스크린샷 사운드와 셔터 사운드를 다시 변경**했다는 이용자 민원이 있어, 신형 OS에서 체감 동작이 달라질 가능성은 열려 있음(추정 — 키 자체 변경은 아니고 사운드 리소스/트리거 조건 변경 가능성).
- **One UI 6/7/8/9에서 키 이름이 바뀌었다는 1차 근거는 발견하지 못함.** 다만 "일부 카메라 펌웨어가 이 설정 키를 반영하지 않으면 무음이 실제로 적용되지 않는다"는 보고가 있어 모델/국가 CSC별로 동작이 다를 수 있음 [[Samsung Members]](https://r1.community.samsung.com/t5/%EA%B0%A4%EB%9F%AD%EC%8B%9C-s/%EB%AC%B4%EC%9D%8C-%EC%84%A4%EC%A0%95-%ED%9E%98%EB%93%9C%EC%8B%9C%EC%A3%A0/td-p/37743193).

### 재부팅/업데이트 후 리셋 여부
- **소프트웨어 업데이트 후에는 값이 1(강제 유음)로 되돌아가는 사례가 다수 보고됨** — 업데이트마다 재적용 필요 [[뽐뿌발 가이드 요약, 복수 출처]](https://r1.community.samsung.com/t5/%ED%8C%81/adb%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%9C-%EC%B9%B4%EB%A9%94%EB%9D%BC-%EB%AC%B4%EC%9D%8C-%EC%84%A4%EC%A0%95-%EB%B0%A9%EB%B2%95-%EC%BB%B4%ED%93%A8%ED%84%B0x/td-p/23261439).
- **일반 재부팅만으로는 리셋되지 않는다**는 보고가 일관됨(값은 system 설정 테이블에 영구 저장되며, 업데이트 시 설정 DB가 초기화/마이그레이션되면서 사라지는 것으로 추정 — 설정 리셋의 정확한 내부 메커니즘은 1차 소스에서 확인되지 않음, 추정).
- 카메라 앱 재시작 필요 여부에 대한 명시적 보고는 확인하지 못함. 일반적으로 system 설정이므로 즉시 반영된다고 보는 사용자 보고가 많으나, 일부는 카메라 앱을 완전히 종료 후 재실행해야 적용된다고 언급(교차 확인 안 됨 — 추정 수준).

### WRITE_SECURE_SETTINGS로 일반 앱이 쓸 수 있는가
- `csc_pref_camera_forced_shuttersound_key`는 **System 설정 테이블**(`Settings.System`)의 키이며, Android는 targetSdk 23+ 앱이 `Settings.System.putString`류로 쓰려면 `WRITE_SETTINGS`(일반 런타임 권한, 사용자가 "다른 앱 위에 그리기"류 화면에서 허용)가 필요하지만, 이 특정 CSC/삼성 전용 키는 **일반 서드파티 권한 경로로 보호되지 않고 `adb shell`(shell uid, 즉 `WRITE_SECURE_SETTINGS` 부여된 앱 또는 Shizuku)로만 안정적으로 기록된다고 보고됨**. 근거: targetSdk 22 이하 구형 앱만 일반 권한으로 System 설정을 직접 쓸 수 있었고, 이후부터는 shell 경로가 표준으로 통용 [[Samsung Members 가이드]](https://r1.community.samsung.com/t5/%ED%8C%81/adb%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%9C-%EC%B9%B4%EB%A9%94%EB%9D%BC-%EB%AC%B4%EC%9D%8C-%EC%84%A4%EC%A0%95-%EB%B0%A9%EB%B2%95-%EC%BB%B4%ED%93%A8%ED%84%B0x/td-p/23261439).
- **실제로 Shizuku 경유로 성공한 사례가 다수 확인됨** — Shizuku(aShell+Shizuku, SystemUI Tuner류 앱)로 `csc_pref_camera_forced_shuttersound_key`를 0으로 쓴 사용자 보고 다수 [[X 사례]](https://x.com/s32x_arch/status/2029137147228545279) [[note.com]](https://note.com/kemoaotic/n/nca5481bff94a?hl=en).
- GOAT가 **WRITE_SECURE_SETTINGS만 부여받은 일반 앱**(Shizuku 없이)이라면: `Settings.System` 키이므로 통상 `WRITE_SETTINGS`로 쓸 수 있는 영역이지만, 이 키가 CSC(삼성 통신사/국가 설정) 레이어에 걸려있어 **일반 앱 권한 경로 자체가 차단되거나 무시될 수 있다는 보고**가 있어 — **결론: shell uid(Shizuku) 경로가 검증된 방법이고, WRITE_SECURE_SETTINGS만으로 되는지는 1차 소스에서 명시적으로 검증된 사례를 찾지 못함(추정 — GOAT 구현 시 실기 테스트 필요)**.

### 한국 법·에티켓 맥락 (간단)
- 한국은 2004년부터 이동통신단말장치 제조사에 **카메라 촬영 시 셔터음을 강제로 60dB 이상 발생**하도록 하는 전파법 시행령/고시가 적용되어 왔고, 이는 **몰카(불법촬영) 방지** 목적의 제조사 규제다(제조사 대상 규제이며, 사용자가 사후에 무음 앱/명령으로 끄는 행위 자체를 직접 처벌하는 별도 조항은 확인되지 않음 — 다만 몰카 범죄에 악용되면 당연히 그 범죄로 처벌됨). 커뮤니티 글들에서도 "몰카 방지용 강제 셔터음"이라는 배경 설명이 반복됨.
- 따라서 GOAT가 이 기능을 제공할 경우, **공공장소·탈의실 등 촬영 민감 구역에서의 오남용 가능성**을 고지하는 문구를 두는 것이 에티켓/리스크 관리 측면에서 바람직함(권고, 법적 의무 여부는 본 조사 범위에서 확정하지 못함 — 추정).

---

## Part B — Galaxy adb/Shizuku 꿀팁 랭킹 (~20종)

범례: **[V]** = URL로 명령어/동작 확인됨, **[I]** = 추정(명령어 패턴은 표준이나 갤럭시 특화 1차 소스 미확인)

### 1. 블로트웨어 비활성화/삭제 — [V]
- 기능: 빅스비, Facebook 시스템 앱, 일부 구글 앱, GOS 등 제거 없이 숨김/정지
- 명령: `pm disable-user --user 0 <package>` (정지, 재부팅 생존) / `pm uninstall -k --user 0 <package>` (사용자 레벨 삭제, 공장초기화 시 복구)
- 권한: Shizuku/shell 필요(`pm` 명령 자체는 shell uid 전용, WRITE_SECURE_SETTINGS만으로는 불가)
- 지속성: `disable-user`는 재부팅 후에도 유지. GOS처럼 **일부 패키지는 재부팅 시 자동 재활성화된다는 보고 있음**(기기별 차이) [[droidwin]](https://droidwin.com/disable-uninstall-samsung-game-optimizing-service-app-via-adb/)
- 위험: 시스템 앱 일부 비활성화 시 연쇄 오류(예: 특정 Bixby 컴포넌트 제거 시 설정 앱 일부 메뉴 비정상) 보고 존재
- One UI: 전 버전 공통 패턴, 패키지명은 모델/OS별로 다름
- 근거: [dcinside S24 글(조회 35,916)](https://gall.dcinside.com/mgallery/board/view/?id=galaxy&no=1290115), [darpan.blog Bixby 가이드](https://darpan.blog/code/guide-remove-bixby-bloatware-from-samsung-galaxy-phones/)

### 2. 카메라 셔터음 무음 — [V] (Part A 참조)

### 3. 애니메이션 배율 조정 — [V]
- 기능: 창/전환/애니메이터 속도를 0(끔)~임의 소수(0.25, 0.5, 0.85 등)로 조절해 체감 반응성 개선
- 명령: `settings put global window_animation_scale 0.5`, `transition_animation_scale 0.5`, `animator_duration_scale 0.5`
- 권한: WRITE_SECURE_SETTINGS로 가능(`global` 테이블, SetEdit 류 앱이 `WRITE_SECURE_SETTINGS` 권한만으로 동작)
- 지속성: 영구(수동 변경 전까지), 개발자 옵션 토글과 동일 저장소 공유
- 위험: 과도하게 낮추면 일부 전환 애니메이션 중 UI가 깜빡이거나 멈춘 것처럼 보일 수 있음
- One UI: 전 버전 공통
- 근거: [클리앙(조회 11,474)](https://www.clien.net/service/board/cm_andro/17948406), [XDA](https://xdaforums.com/t/animations-set-scale-set-to-25.4365695/)

### 4. 주사율 강제(최대/최소 고정) — [V]
- 기능: 120Hz 등 피크 주사율을 강제 고정하거나, 적응형 주사율의 최소값을 올려 더 매끄럽게 유지
- 명령: `settings put system peak_refresh_rate 120.0`, `settings put system min_refresh_rate 120.0`
- 권한: WRITE_SECURE_SETTINGS (system 테이블이지만 이 키들은 공개 API로도 노출되어 비교적 수월하게 기록됨 — 다만 삼성 기기별로 `min_refresh_rate` 반응이 다르다는 보고 있음)
- 지속성: 영구, 재부팅 생존
- 위험: 배터리 소모 증가, 일부 기기에서 OS 설정 메뉴값과 충돌 시 원래 UI 토글이 "표준"으로 안 보일 수 있음
- One UI: Galaxy S20 이후 90/120Hz 지원 모델
- 근거: [DroidWin](https://droidwin.com/force-120hz-refresh-rate-in-any-android-via-adb-commands/)

### 5. GOS(Game Optimizing Service) 비활성화 — [V]
- 기능: 게임 중 해상도/프레임 제한을 거는 GOS를 정지해 성능 제한 해제
- 명령: `pm disable-user --user 0 com.samsung.android.game.gos` (또는 `com.enhance.gameservice`, 모델별 패키지명 차이)
- 권한: shell(Shizuku) 필요
- 지속성: **재부팅 시 자동으로 다시 활성화된다는 보고 다수** — 완전 차단은 uninstall이 필요하나 이 또한 OS 업데이트로 복구될 수 있음
- 위험: 발열 증가, 배터리 소모 증가 논쟁 (찬반 혼재)
- One UI: Galaxy S20~ 대 전반 (One UI 4 GOS 논란 이후 지속)
- 근거: [droidwin](https://droidwin.com/disable-uninstall-samsung-game-optimizing-service-app-via-adb/), [Samsung Members(조회 6,767)](https://r2.community.samsung.com/t5/Gaming-Hub/Disable-game-optimizing-service/td-p/13299427)

### 6. 상태표시줄 아이콘 숨기기 (`icon_blacklist`) — [V]
- 기능: NFC/블루투스/알람/회전 등 특정 아이콘 숨김
- 명령: `settings put secure icon_blacklist nfc,bluetooth,rotate` / 복구 `settings put secure icon_blacklist null`
- 권한: WRITE_SECURE_SETTINGS (secure 테이블, 표준 허용 대상)
- 지속성: **일부 구형 기기(S10/S20, One UI 2.1 전후)는 재부팅 시 리셋된다는 보고** — 최신 기기 지속성은 교차 확인 부족(추정)
- 위험: 낮음(토글성 변경)
- One UI: 거의 전 버전, 단 일부 신형에서 Good Lock QuickStar가 더 안정적 대체재로 언급됨
- 근거: [XDA 다수 스레드](https://xdaforums.com/t/guide-hiding-status-bar-icons.3853258/)

### 7. 배터리 보호(충전 상한 %) — [I]
- 기능: 85%(구형)/80·85·90·95%(One UI 7+) 충전 상한
- 명령: 공식 UI 토글이 One UI 6.1+부터 제공되어 **adb 명령어로 설정하는 1차 공개 키 문서는 확인하지 못함** — Settings UI 백엔드 키 존재 추정이나 공개 `settings put` 커맨드 확인 불가. **GOAT가 adb로 제공하려면 실기 역공학(key 덤프)이 선행 필요** — 현재는 "설정 UI로 유도" 수준 권고
- 근거: [Samsung 공식](https://www.samsung.com/ae/support/mobile-devices/what-are-the-specific-differences-of-the-battery-protection-options/)

### 8. 화면 해상도/밀도 변경 — [V]
- 기능: 해상도 낮춰 배터리/발열/성능 개선(게이머용), 또는 밀도 낮춰 화면에 더 많은 정보 표시
- 명령: `wm size 1080x2340`, `wm density 420` (density 범위 대략 120~600)
- 권한: `wm` 명령도 shell 전용(Shizuku 필요, WRITE_SECURE_SETTINGS만으로는 `wm` 셸 명령 자체를 실행할 수 없음 — 단, GOAT가 이미 가진 `setUserCustomDensity`처럼 **삼성 전용 API로 앱별 density만 조정하는 경로는 이미 보유**)
- 지속성: 영구, 재부팅 생존
- 위험: 밀도를 과도히 낮추면 일부 UI 요소 비정상 렌더링, 낮은 해상도는 스크린샷/캐스팅 화질 저하
- 근거: [XDA](https://xdaforums.com/t/lower-your-resolution-the-proper-way-for-gamers-with-adb.4083283/), [blog.9wd.eu](https://blog.9wd.eu/posts/android-screen-denisty/)

### 9. Private DNS 설정 — [V]
- 기능: DNS-over-TLS로 Cloudflare 등 사설 DNS 강제, 광고/트래킹 차단 DNS와 병행 가능
- 명령: `settings put global private_dns_mode hostname`, `settings put global private_dns_specifier one.one.one.one`
- 권한: WRITE_SECURE_SETTINGS(global 테이블)
- 지속성: 영구
- 위험: 낮음, 일부 공공 와이파이에서 연결 실패 가능
- 근거: [Cloudflare 공식](https://developers.cloudflare.com/1.1.1.1/setup/android/)

### 10. 앱별 다크모드 강제 오버라이드 — [I]
- 기능: 다크테마 미지원 앱을 강제로 다크 렌더링
- 명령: 표준 `settings put secure ui_night_mode 2` + 개발자 옵션 "Override force-dark"는 UI 토글이 일반적이며, 특정 앱만 강제하는 공개 `settings put` 키는 1차 소스에서 확인 못함 — `debug.hwui.force_dark`는 `setprop`(루트 필요 소지 있음, 비루트 지속성 불확실)으로 보고됨
- 근거: [9to5Google](https://9to5google.com/2020/02/17/how-to-darq-force-dark-android-10-without-root/) — **삼성 특화 사례는 확인 못함, 추정 비중 높음**

### 11. 위치 등 앱 권한 "ignore" 처리 (appops) — [V]
- 기능: 특정 앱의 위치/카메라 권한을 요청 시 "무시"로 처리해 조용히 차단(앱이 거부 여부를 감지하지 못함)
- 명령: `appops set <package> android:fine_location ignore`
- 권한: shell 전용(Shizuku)
- 지속성: 영구(앱별 appops 테이블)
- 위험: 앱이 권한 거부를 감지해 재요청 루프에 빠지는 경우 있음(표준 "deny"가 아니라 "ignore"라 오히려 일부 앱에서 크래시 유발 가능)
- 근거: [TilesOrganization wiki](https://github.com/TilesOrganization/support/wiki/How-to-use-ADB-to-grant-permissions)

### 12. 화면 자동 꺼짐 시간 제한 초과 설정 — [I]
- 기능: UI 최대값(10분/15분) 이상으로 screen timeout 연장
- 명령: `settings put system screen_off_timeout <ms>` (예: 1800000 = 30분)
- 권한: WRITE_SECURE_SETTINGS로 가능(system 테이블, 표준 공개 키)
- 지속성: 영구
- 위험: 배터리 소모, 디스플레이 번인 우려(사용자 책임 고지 필요)
- 근거: 표준 AOSP 키로 널리 알려짐(범용 안드로이드 지식, 갤럭시 특화 1차 소스 추가 확인은 생략)

### 13. 알 수 없는 출처 설치 없이 "강제 중지 방지" 등 알림 숨김/관리 — [I]
- 기능: 특정 패키지 알림 채널 강제 설정
- 명령: `cmd notification` 계열은 버전별로 상이 — 공개 커맨드 체계 변동 많아 1차 확인 보류
- 근거: 확인 부족, 추정으로만 표시

### 14. 삼성 DeX 유선 없이 강제 활성화(일부 중급기) — [I]
- 기능: DeX 미지원/제한 기기에서 DeX 강제 활성화 시도(모델별 성공률 상이)
- 명령: 모델별 상이, 공개된 단일 표준 명령 없음(펌웨어 depend)
- 근거: 1차 소스 확보 못함 — **추정 항목, 신뢰도 낮음**

### 15. 삼성페이 스와이프(엣지 스와이프 투 페이) 비활성화 — [I]
- 기능: 화면 하단 스와이프로 삼성페이 호출되는 동작 차단
- 명령: 일반적으로 삼성페이 앱 설정 UI 내 토글로 제공되어 adb 전용 수요/문서 확인 못함
- 근거: 확인 부족

### 16. RAM Plus(가상메모리) 끄기/조정 — [I]
- 기능: 백그라운드 앱 유지력 개선 또는 메모리 압박 완화
- 명령: UI 토글 기반, adb 공개 키 1차 확인 못함(XDA 가이드에서 "RAM Plus 끄기"를 권고하나 UI 경로)
- 근거: [XDA Samsung One UI 최적화 가이드](https://forum.xda-developers.com/t/samsung-galaxy-one-ui-optimization-guide.4376755/page-83) — UI 권고, adb 키는 미확인

### 17. 디버그/진단(Usage & Diagnostics) 비활성화 — [V]
- 기능: 삼성 사용 데이터 전송 끄기
- 명령: XDA 가이드에서 `settings put secure` 계열로 끄는 방법 언급(정확한 단일 키는 가이드 열람 제한으로 재확인 필요)
- 근거: [XDA Definitive OneUI Guide](https://xdaforums.com/t/definitive-oneui-guide-tweaks-optimization-privacy.4685716/) — 접속 제한(403)으로 원문 직접 확인은 못했고 검색 스니펫 기준

### 18. 앱 캐시 일괄 정리 — [V]
- 기능: 전체 앱 캐시 트리밍
- 명령: `pm trim-caches 999G`
- 권한: shell
- 지속성: 1회성 동작(주기적 실행 가능)
- 근거: [XDA Definitive OneUI Guide 스니펫](https://xdaforums.com/t/definitive-oneui-guide-tweaks-optimization-privacy.4685716/)

### 19. 접근성 "애니메이션 제거" 공식 토글과 adb 조합 — [V]
- 기능: 항목 3과 중복이나, 접근성 메뉴의 "애니메이션 제거"는 완전히 0으로 고정 — adb로는 세분화된 중간값(0.5~0.85 등) 설정이 핵심 차별점
- 근거: [클리앙](https://www.clien.net/service/board/cm_andro/17948406)

### 20. 네비게이션 제스처 세부조정(Good Lock NavStar 영역을 adb로 보완) — [I]
- 기능: One UI 6.1에서 제거된 "하단에서 스와이프" 제스처 힌트 등을 복원 시도
- 명령: 공개된 단일 `settings put` 키 확인 못함 — Good Lock NavStar 앱이 표준 해법으로 보고됨(adb 대체 경로 미확인)
- 근거: [Samsung Community](https://r2.community.samsung.com/t5/Tips-How-to/How-to-enable-swipe-gestures-Swipe-from-bottom-Gesture-hints-on/bc-p/16235212)

---

## 조사 한계 (사실로 보고)
- XDA "Definitive OneUI Guide" 원문은 403으로 직접 열람하지 못해 검색 스니펫만 반영(항목 17, 18은 교차검증 1회만 수행).
- Reddit(r/GalaxyFold, r/samsung 등) 직접 게시물 URL/투표수는 WebSearch 결과에 노출되지 않아(삼성 공식 커뮤니티·XDA·dcinside·클리앙으로 결과가 쏠림) 조사자가 직접 집계하지 못함 — TOP 10 순위는 확보 가능한 조회수/추천수 기준으로 산정.
- Part A의 "카메라 앱 재시작 필요 여부"와 "One UI 6/7/8/9 키 이름 변경 여부"는 명시적 1차 확인을 하지 못해 추정으로 남김.

---

## Part C — 구현 전 재조사(2026-10-12, GOAT 4~10번 기능용)

출처는 AOSP 소스·오픈소스 앱 README·XDA·클리앙·뽐뿌. **[V]** 검증, **[I]** 추정.

- 주사율: `system peak_refresh_rate`/`min_refresh_rate`는 AOSP DisplayModeDirector가 상·하한 투표로 쓴다 [V]. One UI '화면 움직임 부드럽게'는 `secure refresh_rate_mode`(0 표준·1 적응형·2 고주사율 고정)를 읽는다는 보고가 다수 [I, Adaptive-Hz·XDA]. 절전 모드에서는 60Hz로 내려가는 기종이 있다 [I, GalaxyMaxHz].
- 충전 상한: 공개 키 없음. amply PR #125 역공학으로 `protect_battery`(0 끔·1 최대·2 자동 최대·3 기본) + `battery_protection_threshold`(80/85/90/95) [I, 단일 출처]. 기기 실측 필요.
- 다크 모드 강제: `setprop debug.hwui.force_dark true`는 셸에서 동작하고 재부팅 때 사라진다 [V/I]. 앱별 강제의 공개 adb 키는 확인 못 함.
- 상태 표시줄 아이콘: `secure icon_blacklist` 동작 [V]. 삼성은 재부팅 뒤 풀린다는 보고가 있다 [I, 클리앙]. 확인된 슬롯 이름: rotate, bluetooth, nfc, alarm_clock, volume, wifi, mobile, location, hotspot, data_saver, ims_volte, headset, cast, vpn, zen, managed_profile, sensors_off, sync_active, mute, ethernet, microphone, dnd, clock.
- GOS: com.samsung.android.game.gos(코어)·gametools(Game Booster)·gamehome(Game Launcher) [V]. disable-user가 재부팅에 살아남는지는 보고가 엇갈림 [I].
- 해상도: `wm size`는 WRITE_SECURE_SETTINGS로 동작(Settings.Secure display_size_forced) [V]. density는 해상도 비율대로 함께 낮춰야 화면 크기가 유지된다 [I, 관행].
- 개인 DNS: `global private_dns_mode`(off·opportunistic·hostname) + `private_dns_specifier` [V]. SKT 망 + AdGuard 야간 불안정 보고 1건 [I].

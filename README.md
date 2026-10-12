# GOAT

<sub>**Galaxy Optimization Advanced Tool** — 갤럭시를 더 편리하게 사용할 수 있도록 지원하는 도구 모음</sub>

<sub>GOAT는 갤럭시 기본 설정만으로는 불편하거나 PC 명령이 필요한 작업을 기기 내에서 처리하는 앱입니다. ROOT 없이, 무료 앱 Shizuku 또는 PC의 adb로 최초 한 번만 권한을 부여하면 됩니다. 변경한 설정은 재부팅 후에도 유지됩니다.</sub>

| <sub>메뉴</sub> | <sub>기능 설명</sub> | <sub>요구 사항</sub> |
|---|---|---|
| <sub>**고급 화면 배율 설정**</sub> | <sub>폴드의 외부·내부 화면에서 글자와 아이콘이 동일한 크기로 표시되도록 두 화면의 DPI를 일치시킵니다. 접거나 펼쳐도, 재부팅해도 다시 맞춥니다. 폴드가 아닌 기기에서는 DPI 하나를 설정합니다.</sub> | <sub>시스템 설정 권한(1회)</sub> |
| <sub>**앱별 화면 배율 설정**</sub> | <sub>삼성 '앱 화면 크게/작게'와 같이 앱마다 화면 배율을 개별적으로 설정하고, 알림창에서 현재 앱의 배율을 즉시 변경합니다. 홈 화면 아이콘 크기만 별도로 변경할 수도 있습니다.</sub> | <sub>One UI 9 이상, Shizuku</sub> |
| <sub>**해상도 낮추기**</sub> | <sub>글자·아이콘 크기는 유지한 채 해상도만 낮춥니다. 15초 이내에 '유지'를 선택하지 않으면 원래 설정으로 복원됩니다.</sub> | <sub>시스템 설정 권한(1회), 폴드 제외</sub> |
| <sub>**주사율 고정**</sub> | <sub>항상 최고 주사율(예: 120Hz) 또는 60Hz 절전 모드로 고정하고, 현재 주사율을 실시간으로 표시합니다.</sub> | <sub>Shizuku</sub> |
| <sub>**애니메이션 속도**</sub> | <sub>창·화면 전환·앱 내 애니메이션 속도를 일괄(끔~1.5배) 또는 개별(0~2배)로 조정합니다.</sub> | <sub>시스템 설정 권한(1회)</sub> |
| <sub>**앱 다크 모드 강제**</sub> | <sub>다크 모드를 지원하지 않는 앱도 시스템이 어둡게 렌더링하도록 합니다. 시스템 다크 모드도 함께 활성화하며, 비활성화 시 원래 상태로 복원합니다.</sub> | <sub>Shizuku</sub> |
| <sub>**흔들어서 화면 회전**</sub> | <sub>평상시에는 회전을 잠금 상태로 유지하고, 기기를 원하는 방향으로 든 채 흔들 때만 해당 방향으로 회전한 뒤 다시 잠금 처리합니다. 큰 화면(펼친 폴드·태블릿)에서만 동작하도록 설정할 수도 있습니다.</sub> | <sub>시스템 설정 권한(1회)</sub> |
| <sub>**카메라 촬영음 끄기**</sub> | <sub>국내 출시 갤럭시의 강제 촬영음을 비활성화하여 촬영음이 기기 소리 설정(무음·진동)을 따르도록 합니다. 업데이트로 재활성화되면 자동으로 다시 비활성화합니다.</sub> | <sub>Shizuku(또는 PC adb 명령 한 번)</sub> |
| <sub>**기본 앱 정리**</sub> | <sub>사용하지 않는 삼성·제휴 기본 앱(빅스비·AR·게임 런처·페이스북 등)을 삭제하지 않고 비활성화합니다. 스위치로 언제든 다시 활성화할 수 있습니다.</sub> | <sub>Shizuku</sub> |
| <sub>**GOS 끄기**</sub> | <sub>게임 중 성능을 조절하는 게임 최적화 서비스를 비활성화하고, 재부팅 후 재활성화되면 다시 비활성화합니다.</sub> | <sub>Shizuku</sub> |
| <sub>**잠자기 모드 끄기**</sub> | <sub>화면이 꺼진 동안 앱 동작을 지연시키는 잠자기 모드(Doze)를 비활성화하여 알림이 지연되지 않도록 합니다. 대기 중 배터리 소모는 증가합니다.</sub> | <sub>Shizuku</sub> |
| <sub>**핫스팟 제한 우회**</sub> | <sub>통신사가 핫스팟을 별도 APN(dun)으로 제한한 경우, 현재 인터넷 APN과 동일한 값의 dun 유형 APN을 추가하여 일반 데이터 경로를 사용하도록 합니다. 통신사 약관 위반 소지가 있어 최초 사용 시 경고에 동의해야 합니다.</sub> | <sub>Shizuku</sub> |

<sub>폴드는 외부 화면과 내부 화면의 픽셀 밀도(PPI)가 서로 달라, 동일한 DPI를 적용하더라도 두 화면에서 글자와 아이콘의 실제 크기가 다르게 표시됩니다. `adb shell wm density`로 DPI를 변경해도 재부팅 시 초기화되며, 두 화면을 개별적으로 맞추는 작업도 번거롭습니다. 고급 화면 배율 설정은 외부 화면 DPI 하나만 지정하면 내부 화면 DPI를 두 화면의 해상도와 PPI로 계산하여 자동으로 맞춥니다.</sub>

<p align="center">
  <img src="docs/screenshots/home.png" width="22%" alt="홈" />
  <img src="docs/screenshots/menu.png" width="22%" alt="메뉴" />
  <img src="docs/screenshots/help.png" width="22%" alt="사용 방법" />
  <img src="docs/screenshots/advanced.png" width="22%" alt="고급 정보" />
</p>

<sub>변경 사항은 [변경 기록(CHANGELOG.md)](CHANGELOG.md)에서 확인할 수 있습니다.</sub>

### adb 명령과의 비교

| | <sub>`adb shell wm density` 단독 사용</sub> | <sub>GOAT</sub> |
|---|---|---|
| <sub>바꿀 때마다</sub> | <sub>PC 연결 후 명령 재입력</sub> | <sub>기기 내 슬라이더로 즉시 조절</sub> |
| <sub>재부팅 후</sub> | <sub>기기 기본값으로 복원되어 재입력 필요</sub> | <sub>부팅 후 자동 재적용</sub> |
| <sub>외부·내부 화면</sub> | <sub>동일 값 입력 시 두 화면의 실제 크기가 다르게 표시됨. 값을 개별적으로 계산해야 함</sub> | <sub>두 화면의 PPI 비율로 내부 DPI 자동 계산</sub> |
| <sub>디스플레이 번호</sub> | <sub>접고 펼 때 `-d 0`/`-d 1`이 가리키는 화면이 바뀌어 혼동 발생</sub> | <sub>해상도 기반으로 외부·내부 화면을 식별하여 각각 적용</sub> |
| <sub>다른 설정에서 바뀌면</sub> | <sub>알 수 없음</sub> | <sub>감지 후 '이 값을 기준으로' 또는 '복원' 중 선택하도록 안내</sub> |
| <sub>맞았는지 확인</sub> | <sub>육안으로 판단</sub> | <sub>크기 테스트(두 화면의 줄 수, 카드로 맞춘 자)</sub> |
| <sub>되돌리기</sub> | <sub>`wm density reset` 명령을 숙지해야 함</sub> | <sub>⋮ 메뉴 → 초기화 → 기본 DPI로 복원</sub> |
| <sub>PC가 필요한 때</sub> | <sub>매번</sub> | <sub>불필요(Shizuku 사용 시) 또는 최초 권한 부여 시 1회</sub> |

### 주요 기능

#### 고급 화면 배율 설정

- <sub>**외부 화면 기준 자동 계산**: 외부 화면 DPI만 지정하면 내부 화면 DPI는 `외부 DPI × 내부 PPI ÷ 외부 PPI`로 계산됩니다. 필요한 경우 내부 화면을 ±20 범위에서 보정할 수 있습니다.</sub>
- <sub>**두 화면에 각각 적용**: 폴드는 화면(디스플레이 ID)별로 DPI를 개별 저장하므로, 확인된 두 화면에 각각 적용합니다.</sub>
- <sub>**자동 적용**: 부팅 후(0·5·15·30·60초 확인), 접기/펼치기 후, 다른 앱이 DPI를 변경했을 때 재확인합니다. 감지는 이벤트 방식으로 동작하므로 상주하거나 주기적으로 활성화되지 않습니다.</sub>
- <sub>**외부 DPI 변경 감지**: 시스템 설정의 화면 크기 등으로 DPI가 변경되면 덮어쓰지 않고, 해당 값을 기준으로 할지 이전 값으로 복원할지 알림 및 홈 카드로 확인합니다.</sub>
- <sub>**크기 테스트**: 두 화면은 실제 높이가 거의 동일하므로, 크기가 일치하면 한 화면에 표시되는 줄 수도 동일합니다. 전체 화면에 번호가 매겨진 줄을 표시하여 접고 펼치며 비교할 수 있으며, 카드(짧은 변 54mm)로 맞춘 자를 이용해 실제 길이를 측정할 수도 있습니다.</sub>
- <sub>**권한 설정 안내**: 연결 방식(USB/무선)과 PC 운영체제(Windows/Mac/Linux)에 맞춘 단계별 안내, 명령 복사, 자주 발생하는 오류 해결 방법을 앱 내에서 제공합니다. 안내문을 PC로 전송하거나 메일로 발송할 수도 있습니다.</sub>

#### 앱별 화면 배율 설정

- <sub>삼성 '앱 화면 크게/작게'와 동일한 앱별 크기를 GOAT에서 변경합니다(One UI 9 이상, Shizuku 필요).</sub>
- <sub>최근 사용 순 목록, 변경된 앱 모아 보기, 앱 이름 검색, 일괄 기본값 복원 기능을 지원합니다.</sub>
- <sub>알림창의 [작게]·[기본]·[크게]로 현재 사용 중인 앱(GOAT 자신 포함)의 배율을 즉시 변경합니다.</sub>

#### 해상도 낮추기

- <sub>기본 화면의 강제 해상도(`wm size`와 동일한 호출)를 FHD+·900·HD+ 또는 85%·75%로 낮추고, 표시 밀도도 동일한 비율로 축소하여 글자·아이콘 크기를 유지합니다. 고급 화면 배율 설정을 사용 중인 경우 해당 목표값도 동일한 비율로 맞추어 서로 덮어쓰지 않도록 합니다.</sub>
- <sub>변경 후 15초 이내에 '유지'를 선택하지 않으면 원래 설정으로 복원합니다(앱 카운트다운, 예약 작업, 잠금 해제 전 부팅 수신기의 3중 안전장치). 복원 시에는 GOAT가 변경 직전 해상도(삼성 설정의 FHD+ 포함)와 밀도로 되돌립니다. 폴드는 지원하지 않습니다.</sub>

#### 주사율 고정

- <sub>안드로이드 공통 설정 `peak_refresh_rate`·`min_refresh_rate`와 삼성 `refresh_rate_mode`(삼성 기기만)를 Shizuku 셸로 일괄 변경합니다. '항상 최고'(예: 항상 120Hz), '60Hz 절전', '시스템 기본'(초기값으로 복원하고 기존에 없던 키는 삭제) 중에서 선택하며, 현재 화면 주사율을 실시간으로 표시합니다.</sub>
- <sub>앱 외부에서 변경한 값은 덮어쓰지 않습니다. 최고 주사율이 60Hz인 화면은 변경할 항목이 없음을 안내합니다.</sub>

#### 애니메이션 속도

- <sub>개발자 옵션의 창·전환·Animator 배율(`settings global`)을 변경합니다. 커뮤니티에서 가장 많이 사용하는 0.5배를 일괄 선택할 수 있으며, '하나씩 맞추기'으로 0.05배 단위 조절도 지원합니다.</sub>

#### 앱 다크 모드 강제

- <sub>개발자 옵션 '강제 다크 모드 재정의'와 동일한 `debug.hwui.force_dark`를 Shizuku `setprop`으로 활성화합니다. 안드로이드는 시스템 다크 모드일 때만 이 값을 적용하므로, 활성화 시 다크 모드가 꺼져 있으면 함께 활성화하고(`cmd uimode night yes`), 비활성화 시 이전 방식(꺼짐·자동·예약 시각)으로 복원합니다. 안드로이드 16 이상에서는 '더 많은 앱을 어둡게'(`accessibility_force_invert_color_enabled`)도 함께 활성화합니다.</sub>
- <sub>재부팅 시 초기화되는 값이므로 부팅마다 1회 재활성화합니다(동일 부팅 내의 직접 변경은 유지). 앱별 선택 기능은 공식 경로가 없어 모든 앱에 일괄 적용됩니다.</sub>
- <sub>시스템이 색상을 자동으로 변경하는 방식이므로 일부 앱에서는 글자가 보이지 않거나 흰 사각형이 나타날 수 있습니다. 해당 화면과 사용 방법에서 사전에 안내합니다.</sub>

#### 흔들어서 화면 회전

- <sub>기기를 원하는 방향으로 든 채 흔들면 그 순간만 자동 회전을 활성화하여 해당 방향으로 회전한 뒤 다시 잠금 처리합니다. 세로로 든 채 흔들면 세로 상태를 유지합니다.</sub>
- <sub>가볍게 두드리거나 부딪히거나 떨어뜨린 경우, 걷기·차량 진동은 제외하고, 빠르게 흔드는 동작과 천천히 크게 흔드는 동작은 모두 인식합니다.</sub>
- <sub>민감도(1~30)와 고급 설정(흔드는 횟수·속도·손목 회전 기준·탭 제외·큰 화면 전용), 4초간 흔들어 이 값들을 일괄 설정하는 자동 맞추기 기능을 지원합니다.</sub>
- <sub>'큰 화면에서만 회전'을 활성화하면 짧은 변이 600dp 이상인 화면(펼친 폴드·태블릿)에서만 회전하며, 접은 화면에서는 흔들어도 회전하지 않습니다.</sub>
- <sub>자동 맞추기 화면의 물방울은 흔들림·충격이 감지될 때마다 튀는 모습으로 판정 결과를 시각적으로 표시합니다. 물의 양·점도·표면장력·흩어짐·튀는 세기·중력·출렁임·복원력은 조절바로 변경할 수 있습니다(판정에는 영향 없음).</sub>
- <sub>회전이 완료되면 흔든 리듬 그대로 진동합니다(휙 휘힉 → 톡 토독). 진동 세기는 0~10 단계로 조절합니다.</sub>

#### 카메라 촬영음 끄기

- <sub>숨은 시스템 설정 `csc_pref_camera_forced_shuttersound_key`를 0으로 변경하여, 무음·진동 모드에서는 촬영음이 발생하지 않도록 합니다(소리 모드에서는 그대로 발생). 일반 앱 권한으로는 변경할 수 없어 Shizuku 셸 권한으로 `settings put system`을 실행하며, Shizuku가 없는 경우 PC 명령(`adb shell settings put system csc_pref_camera_forced_shuttersound_key 0`)을 안내합니다.</sub>
- <sub>업데이트 이후 값이 1로 복원되는 기기가 있어, '다시 켜지면 자동으로 끄기'를 활성화하면 Shizuku가 연결될 때마다 확인하여 다시 비활성화합니다.</sub>
- <sub>촬영음은 불법 촬영을 방지하기 위한 장치입니다. 타인을 동의 없이 촬영하는 행위는 처벌 대상이며, 앱 내에도 이 안내를 명시합니다.</sub>

#### 기본 앱 정리

- <sub>`pm disable-user --user 0`으로 비활성화하고 `pm enable`로 복원합니다(삭제하지 않음). 비활성화해도 전화·문자·설정·카메라 기능에 영향이 없는 앱만 목록에 포함했으며, 삼성페이·클라우드처럼 영향이 있는 앱은 '주의'로 표시하여 재확인을 요청합니다.</sub>

#### GOS 끄기

- <sub>게임 최적화 서비스(`com.samsung.android.game.gos`, 이전 `com.enhance.gameservice`)를 기본 앱 정리와 동일한 `pm disable-user`로 비활성화합니다. 두 화면이 동일한 상태를 공유합니다. 게임 런처·게임 부스터는 영향을 받지 않습니다.</sub>
- <sub>재부팅 후 재활성화되는 기기를 위해 부팅마다 Shizuku 연결 시 1회 다시 비활성화합니다.</sub>

#### 잠자기 모드 끄기

- <sub>`dumpsys deviceidle disable`(깊은·가벼운 잠자기 모두)을 Shizuku 셸로 실행하고, `dumpsys deviceidle enabled deep|light`로 재조회하여 확인합니다. 비활성화 시 배터리 영향에 대해 재확인을 요청합니다.</sub>
- <sub>메모리에만 유지되는 설정이므로 재부팅하면 다시 활성화됩니다. 비활성화 상태였다면 부팅마다 Shizuku 연결 시 1회 다시 비활성화하며, 동일 부팅 내에서 PC 등으로 직접 활성화한 경우는 그대로 유지합니다.</sub>
- <sub>특정 앱의 알림만 지연되는 경우에는 해당 앱의 배터리 설정을 '제한 없음'으로 두는 방식이 배터리 측면에서 유리하다고 화면에서 안내합니다.</sub>


#### 핫스팟 제한 우회

- <sub>Shizuku 셸로 현재 사용 중인 APN(`content://telephony/carriers/preferapn`)을 읽고, 동일한 값에 유형만 `dun`인 'GOAT 테더링' APN을 추가한 뒤 다시 조회하여 확인합니다.</sub>
- <sub>기기가 셸의 APN 쓰기를 차단하는 경우, 통신사 기기에서 숨겨진 APN 추가 화면(`am start -a android.intent.action.INSERT -d content://telephony/carriers --ei simId -1`)을 열고 이름·APN·전각 `ｄｕｎ`을 복사하여 입력하도록 안내합니다.</sub>
- <sub>메뉴는 숨기지 않으며, 최초 진입 시 통신사 약관 위반 가능성과 사용자 책임을 고지하고 동의한 경우에만 추가 기능을 표시합니다. 추가한 APN은 화면에서 삭제할 수 있고, 삭제할 수 없는 경우 '모바일 네트워크 설정 초기화'로 원래 상태로 복원됩니다.</sub>
#### 앱 내 업데이트

- <sub>홈을 열거나 홈으로 복귀할 때(앱을 새로 실행하면 즉시, 이후에는 30분 간격으로) GitHub 릴리스의 최신 버전(`releases/latest`)을 확인하고, 현재 앱보다 최신이면 홈 상단에 [지금 업데이트] 카드를 표시합니다. 앱 설정 → 업데이트에서 직접 확인할 수도 있습니다.</sub>
- <sub>APK를 앱 캐시에 내려받아 크기와 릴리스 노트의 SHA-256이 일치할 때만 설치합니다. Shizuku가 연결되어 있으면 `pm install -r`로 별도 확인 없이 설치하며(완료 후 GOAT가 재시작됩니다), 연결되어 있지 않으면 시스템 설치 창을 표시합니다(최초 1회 'GOAT의 앱 설치 허용' 필요).</sub>
- <sub>동일한 키로 서명된 버전만 설치되므로, 출처가 다른 APK로 대체되지 않습니다.</sub>

#### 공통

- <sub>**문제 신고**: 진단 파일(기록 파일을 활성화한 경우 당일 기록 포함)을 첨부한 메일을 즉시 작성합니다.</sub>
- <sub>**앱 설정**(사이드바 맨 아래): '설정'에 업데이트 확인, UI 유연성(움직일 때의 출렁이는 정도 0~200%, 기본 100%, 속도는 유지, 모든 리퀴드 요소에 적용), 햅틱 세기(0~200%, 기본 100%), 기록 파일 저장 옵션을, '정보'에 앱 정보·변경 기록·필요 권한을 모았습니다.</sub>
- <sub>**기록 파일 저장**(앱 설정, 기본값 비활성화): 활성화하면 기능별 작동 기록을 `다운로드/GOAT/goat-날짜-log.txt`에 날짜별로 저장합니다. 비활성화하면 다운로드 폴더에 파일을 생성하지 않고, 앱 내부에만 간략히 보관합니다.</sub>
- <sub>**화면 구성**: 모든 기능 화면은 소개 → 권한·연결 → 주 조작 → 세부 옵션 → 고급 설정 → 주의 순서로 구성되며, ⋮ 메뉴는 사용 방법 → 권한 설정 → 문제 신고 → 초기화(구분선 아래) 순서입니다.</sub>

### 호환 기기

#### 기능별 호환(폴드가 아닌 갤럭시·One UI 버전)

<sub>코드가 실행 시점에 기기를 확인하여, 지원하지 않는 기기에는 '이 기기에서는 쓸 수 없습니다'와 같은 안내를 화면에 표시합니다. 아래 표는 코드 확인 및 공개 자료 조사 결과이며, 실기기로 확인한 기종은 Galaxy Z Fold8(One UI 9)뿐입니다.</sub>

| <sub>기능</sub> | <sub>S·A·Flip</sub> | <sub>Tab</sub> | <sub>One UI 4~8 (Android 12~16)</sub> | <sub>판정 방법</sub> |
|---|---|---|---|---|
| <sub>고급 화면 배율 설정</sub> | <sub>지원(DPI 하나)</sub> | <sub>지원</sub> | <sub>지원(AOSP 표준 호출)</sub> | <sub>두 화면 맞추기만 힌지 센서로 폴드 판정</sub> |
| <sub>앱별 화면 배율 설정</sub> | <sub>One UI 9 이상</sub> | <sub>One UI 9 이상</sub> | <sub>미지원(삼성 '앱 화면 크게/작게'가 One UI 9에서 도입됨)</sub> | <sub>시스템 함수 존재 여부 확인</sub> |
| <sub>해상도 낮추기</sub> | <sub>지원(A 시리즈 포함)</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>폴드만 제외</sub> |
| <sub>주사율 고정</sub> | <sub>60Hz보다 빠른 화면만</sub> | <sub>동일</sub> | <sub>지원(One UI 6·7은 적응형 주사율이 잠시 낮출 수 있음)</sub> | <sub>지원 주사율 목록</sub> |
| <sub>애니메이션 속도</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>없음(표준 설정)</sub> |
| <sub>앱 다크 모드 강제</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>지원(Android 16부터 '더 많은 앱 어둡게'도 함께)</sub> | <sub>없음</sub> |
| <sub>흔들어서 화면 회전</sub> | <sub>지원</sub> | <sub>지원('큰 화면에서만'으로 구분)</sub> | <sub>지원</sub> | <sub>가속도 센서</sub> |
| <sub>카메라 촬영음 끄기</sub> | <sub>국내·일본 모델만</sub> | <sub>동일</sub> | <sub>버전 무관(국가 설정에 따름)</sub> | <sub>강제 설정값 존재 여부 확인</sub> |
| <sub>기본 앱 정리</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>설치된 앱만 목록에 표시</sub> |
| <sub>GOS 끄기</sub> | <sub>지원</sub> | <sub>GOS 미탑재 Tab은 안내만 제공</sub> | <sub>지원</sub> | <sub>GOS 패키지 설치 여부</sub> |
| <sub>잠자기 모드 끄기</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>지원(Android 6부터 있는 표준 기능)</sub> | <sub>없음</sub> |
| <sub>핫스팟 제한 우회</sub> | <sub>통신사·요금제에 따라 다름</sub> | <sub>LTE·5G 모델만</sub> | <sub>통신사·버전에 따라 다름(실기기 미확인)</sub> | <sub>셸의 APN 쓰기 허용 여부</sub> |

<sub>Android 12(API 31) 이상이 필요합니다. 고급 화면 배율 설정의 두 화면 맞추기는 Galaxy Z Fold 시리즈를, 앱별 화면 배율 설정은 One UI 9 이상의 갤럭시를 대상으로 합니다. 해상도 낮추기는 폴드를 지원하지 않으며, 주사율 고정은 60Hz보다 빠른 화면에서만 유효합니다. 흔들어서 화면 회전은 가속도 센서가 탑재된 기기이면 사용할 수 있습니다. 아래 표는 고급 화면 배율 설정 기준입니다.</sub>

| <sub>기기</sub> | <sub>상태</sub> | <sub>비고</sub> |
|---|---|---|
| <sub>Galaxy Z Fold8 (SM-F971N)</sub> | <sub>실기기 확인</sub> | <sub>화면별 적용, 자동 적용, 크기 일치 확인</sub> |
| <sub>Galaxy Z Fold7</sub> | <sub>지원(실기기 미확인)</sub> | <sub>제조사 패널 사양으로 PPI 보정</sub> |
| <sub>Galaxy Z Fold6</sub> | <sub>지원(실기기 미확인)</sub> | <sub>제조사 패널 사양으로 PPI 보정</sub> |
| <sub>Galaxy Z Fold4·Fold5</sub> | <sub>지원(실기기 미확인)</sub> | <sub>제조사 패널 사양으로 PPI 보정</sub> |
| <sub>Galaxy Z Fold3</sub> | <sub>지원(실기기 미확인)</sub> | <sub>제조사 패널 사양으로 PPI 보정</sub> |
| <sub>Galaxy Z Fold2 (Android 12 이상)</sub> | <sub>동작 예상</sub> | <sub>사양표에 없어 기기가 보고하는 PPI로 계산</sub> |
| <sub>그 밖의 폴더블(Pixel Fold 등)</sub> | <sub>동작 예상</sub> | <sub>기기가 보고하는 PPI로 계산. 화면별 저장 방식이 다른 경우 접기/펼치기 시 재적용</sub> |
| <sub>Galaxy Z Flip 시리즈</sub> | <sub>대상 아님</sub> | <sub>외부 화면이 보조 화면으로, 맞출 필요성이 낮음</sub> |
| <sub>Android 11 이하</sub> | <sub>미지원</sub> | <sub>minSdk 31</sub> |

<sub>기기가 보고하는 PPI가 실제 패널과 다를 수 있어, 사양표에 등재된 기종은 제조사 사양(해상도·대각선)으로 PPI를 재계산합니다. 확인된 기기가 추가되면 본 표에 반영합니다. 다른 기기에서 사용하신 경우 앱의 ⋮ 메뉴 → 문제 신고를 통해 진단 파일을 보내 주시기 바랍니다.</sub>

### 설치

1. <sub>[Releases](../../releases/latest)에서 최신 APK를 내려받아 설치합니다(출처를 알 수 없는 앱 설치 허용 필요).</sub>
2. <sub>앱을 실행하면 **권한 설정 필요** 카드가 표시됩니다. [설정 방법]을 선택하고 연결 방법을 고릅니다.</sub>
   - <sub>**Shizuku**(PC 없이): 무료 앱 [Shizuku](https://shizuku.rikka.app/)를 설치하고, 안내에 따라 무선 디버깅으로 페어링·시작한 뒤 GOAT를 허용하면 GOAT가 자동으로 권한을 획득합니다. Wi-Fi가 필요하며, 권한 획득 후에는 Shizuku를 종료해도 됩니다.</sub>
   - <sub>**PC 무선** / **PC 유선**: PC 운영체제에 맞춘 안내에 따라 PC에서 아래 명령을 1회 실행합니다.</sub>

```bash
adb shell pm grant com.labok.goat android.permission.WRITE_SECURE_SETTINGS
```

<sub>이 권한은 일반 권한 창으로는 부여받을 수 없어 셸 권한(Shizuku 또는 adb)이 필요합니다. 재부팅 후에도 유지되며, 권한 부여 후에는 USB/무선 디버깅을 비활성화해도 됩니다. 앱을 삭제하면 권한도 함께 사라지므로, 업데이트 시에는 삭제하지 말고 덮어 설치(`adb install -r` 또는 APK 실행)하시기 바랍니다.</sub>

#### 문제 해결

| <sub>PC에 나온 문구</sub> | <sub>해결</sub> |
|---|---|
| <sub>`protocol fault (couldn't read status message)`</sub> | <sub>기기의 VPN을 비활성화하고, 동일 와이파이 연결 여부를 확인한 뒤 `adb kill-server` → `adb start-server`를 실행합니다. 해결되지 않으면 기기와 PC를 재시작합니다.</sub> |
| <sub>`more than one device/emulator`</sub> | <sub>`adb devices`로 목록을 확인한 뒤 `adb -s <이름> shell pm grant …`와 같이 기기를 지정합니다.</sub> |
| <sub>`unauthorized`</sub> | <sub>기기의 'USB 디버깅 허용' 창에서 허용합니다. 창이 표시되지 않으면 개발자 옵션 → USB 디버깅 권한 승인 취소 후 재연결합니다.</sub> |
| <sub>`'adb' is not recognized`</sub> | <sub>Windows에서는 platform-tools 폴더에서 `.\adb`로 실행합니다.</sub> |

<sub>무선 페어링에는 adb 30 이상이 필요하며, `adb pair`(페어링 팝업의 포트)와 `adb connect`(무선 디버깅 첫 화면의 포트)는 포트가 서로 다릅니다.</sub>

### 사용 방법

1. <sub>**표시 크기**에서 외부 화면 DPI를 설정합니다. 값이 작을수록 글자와 아이콘이 작게(화면이 넓게) 표시됩니다. 내부 화면 DPI는 자동으로 계산되며, 미리보기에서 두 화면의 크기를 비교할 수 있습니다.</sub>
2. <sub>한 번 접었다가 펼쳐 두 화면의 정보를 수집하면 두 화면에 모두 적용됩니다.</sub>
3. <sub>**자동 적용** 스위치를 활성화하면 재부팅·화면 전환·외부 변경 시 자동으로 재조정됩니다.</sub>
4. <sub>크기가 미세하게 다르게 느껴지면 **크기 테스트**로 확인하고 **내부 화면 보정**으로 조정합니다.</sub>

<sub>앱을 실행하면 **홈**에서 모든 기능의 현재 상태를 타일(아이콘 오른쪽에 이름과 상태, 활성화된 기능은 굵은 테두리, 비활성화된 기능은 연한 아이콘)로 확인하고 바로 진입할 수 있습니다. 타일 칸 수는 기능 이름이 한 줄에 모두 표시되는 폭에 맞춥니다. 사이드바(왼쪽 상단 ☰)는 **화면 배율**(고급 화면 배율 설정·앱별 화면 배율 설정), **화면 표시**(해상도 낮추기·주사율 고정·애니메이션 속도·앱 다크 모드 강제), **편의 기능**(흔들어서 화면 회전·카메라 촬영음 끄기), **시스템**(기본 앱 정리·GOS 끄기·잠자기 모드 끄기·핫스팟 제한 우회)으로 구성되며 맨 아래에 **앱 설정**이 있습니다. 폴드 내부 화면·태블릿과 같이 넓은 화면에서는 항상 왼쪽에 펼쳐진 상태로 표시됩니다. 뒤로 가기는 사이드바 닫기 → 홈 → 앱 닫기 순서로 동작합니다. 각 기능의 세부 도구(사용 방법·권한 설정·문제 신고·초기화)는 우측 상단 ⋮ 메뉴에 동일한 순서로 제공됩니다. 표시 크기 조절바는 값만 변경되며 [적용]을 선택해야 반영됩니다. 조절바를 0.75초간 유지하면 앞뒤 10칸을 세밀하게 조정할 수 있는 확대 패널이 열립니다. 사이드바나 팝업이 열리면 배경 화면이 흐려집니다.</sub>

#### 앱별 화면 배율 설정

- <sub>**필요 조건**: 삼성 One UI 9 이상과 Shizuku. 삼성은 앱별 크기를 변경하는 시스템 함수(`IActivityTaskManager.setUserCustomDensity`)에 시스템 전용 권한을 부여해 두어, Shizuku가 제공하는 셸 권한으로 호출합니다. Shizuku가 없는 경우 PC에서 명령 1회 실행으로 GOAT 셸 도우미를 활성화하여 사용할 수도 있습니다.</sub>
- <sub>**Shizuku가 필요한 경우**: 값을 변경할 때만입니다. 설정된 크기는 시스템에 저장되므로 Shizuku가 비활성화되어 있거나 재부팅해도 유지됩니다.</sub>
- <sub>**6단계**: 시스템은 320·360·420·450·480·510 값만 허용합니다. '기본'은 앱별 설정을 삭제합니다.</sub>
- <sub>**접고 펼칠 때**: 앱별 크기는 하나의 고정 DPI이므로 두 화면에 동일한 값이 적용되어, 픽셀 밀도가 다른 두 화면에서는 실제 크기가 다르게 표시됩니다.</sub>
- <sub>**알림창 조절**: 활성화하면 알림에 현재 사용 중인 앱과 크기가 표시됩니다. 포그라운드 앱은 Shizuku 셸 권한으로 등록한 작업 스택 감시(ITaskStackListener)를 통해 식별하므로 주기적으로 확인하지 않습니다. 크기를 변경하면 삼성 동작 방식에 따라 해당 앱이 재시작되며, GOAT가 다시 실행합니다.</sub>

#### 흔들어서 화면 회전

- <sub>**동작 방식**: 흔들면 시스템 자동 회전을 일시적으로 활성화하고(`accelerometer_rotation`), 화면이 회전하면 해당 방향을 `user_rotation`에 반영하여 다시 잠금 처리합니다. 세로로 든 채 흔들면 세로 상태를 유지합니다. 권한은 고급 화면 배율 설정과 동일한 `WRITE_SECURE_SETTINGS`입니다.</sub>
- <sub>**흔들기 판정**: 저역 통과 필터로 중력 성분을 제거하고, 움직임 방향이 전환될 때마다 1회의 휘두름으로 산정합니다. 휘두름마다 세기·길이·날카로움·손목 회전을 기록하여, 단일 샘플 수준의 탭·충격과 떨어뜨림(자유 낙하)은 제외하고, 손목 회전이 발생했거나 충분히 강하게 흔든 경우만 산정합니다. 근접 센서가 가려진 경우(주머니 속 등)는 무시하며, 화면이 꺼지면 센서를 사용하지 않습니다.</sub>
- <sub>**설정**: 민감도(1~30)와 고급 설정(흔드는 횟수 2~4, 속도, 손목 회전 기준, 탭 제외, 큰 화면 전용). '흔들어서 자동으로 맞추기'는 4초간 흔든 세기·리듬·손목 회전을 바탕으로 이 값들을 함께 설정합니다.</sub>
- <sub>**진동**: 흔든 리듬 그대로 발생합니다. 진동 간 최소 간격은 50ms이며, 세기는 로그 곡선의 3단계로 구분되어 가장 강한 휘두름은 클릭, 나머지는 틱으로 처리됩니다(Android 햅틱 지침 기준). 세기 0~10 조절바를 조정하면 예시 진동이 발생합니다.</sub>
- <sub>**알림**: 안드로이드는 알림이 없는 앱의 센서 사용을 중단시키므로 GOAT 상주 서비스(`GoatService`)가 알림 1건을 유지합니다. 알림창 앱별 배율 조절이 활성화되어 있으면 해당 알림을 공유하며, 흔들기 기능만 활성화된 경우에는 소리·진동이 없는 알림 1건만 유지합니다. 접근성 서비스는 사용하지 않습니다(Play 프로텍트가 웹에서 내려받은 접근성 앱의 설치를 차단하기 때문).</sub>

#### 외부 DPI 변경 처리

<sub>앱은 화면별로 마지막으로 적용한 DPI를 저장합니다. 시스템 설정의 화면 크기, 다른 앱, adb 등에서 값이 변경되면 자동으로 덮어쓰지 않고, 홈 상단 카드와 알림을 통해 **이 값 기준으로 설정** 또는 **이전 값으로 복원** 중 선택하도록 합니다. 감지는 WorkManager의 콘텐츠 URI 트리거(`Settings.Secure.display_density_forced`)를 이용하므로 상주 프로세스나 주기적 확인이 없습니다. 부팅 직후 기기가 기본값으로 되돌린 경우는 외부 변경이 아닌 복원 대상으로 처리됩니다.</sub>

#### 안전장치

- <sub>범위(120~640)를 벗어난 값은 저장하지 않으며, 조절바의 최댓값은 화면의 짧은 변이 320dp 이상 유지되는 값(짧은 변 px × 160 ÷ 320)으로 기기별로 결정됩니다.</sub>
- <sub>현재 값이 목표값과 동일하면 재적용하지 않습니다.</sub>
- <sub>자동 적용은 1분당 최대 5회로 제한됩니다(시스템과 상호 덮어쓰는 무한 반복 방지). 사용자가 직접 변경하는 경우에는 제한이 없습니다.</sub>
- <sub>보정 비율이 0.7~1.4를 벗어나거나 결과값이 범위를 초과하면 적용을 보류합니다.</sub>
- <sub>부팅 재확인은 unique work(`dpi_recover`, REPLACE)로 처리되어 작업이 중복되지 않습니다.</sub>

#### 개인정보 및 권한

<sub>저장소·위치 권한은 사용하지 않습니다. 인터넷은 업데이트 확인에만 사용하여 GitHub 릴리스 정보와 APK만 수신하며, 어떠한 데이터도 외부로 전송하지 않습니다. 앱별 화면 배율 설정은 앱 목록과 최근 사용 기록을 기기 내부에서만 읽어 목록을 정렬하고 현재 앱을 식별합니다. 흔들어서 화면 회전은 화면이 켜져 있을 때만 가속도·자이로·근접 센서를 읽으며, 해당 값을 저장하거나 전송하지 않습니다. 로그는 기본적으로 앱 내부에만 간략히 보관되며, 앱 설정의 '기록 파일 남기기'을 활성화한 경우에만 `다운로드/GOAT/`에 파일로 저장됩니다. 로그와 진단 파일은 사용자가 문제 신고를 통해 직접 전송할 때만 메일에 첨부됩니다. 사용하는 권한 전체 목록과 사유는 앱 정보 화면에서 확인할 수 있습니다.</sub>

### DPI 복구

<sub>화면이 너무 작거나 커서 앱을 조작하기 어려우면 PC에서 다음 명령을 실행하십시오.</sub>

```bash
adb shell wm density reset
```

<sub>ADB 없이 복구하려면 앱의 ⋮ 메뉴 → 초기화 → [기본 DPI로 복원]을 선택하거나, 설정 → 디스플레이 → 화면 크게/작게를 사용하십시오.</sub>

### 삭제

```bash
adb uninstall com.labok.goat
```

<sub>단말의 설정 → 애플리케이션에서 삭제할 수도 있습니다. 삭제 후에도 마지막으로 적용된 강제 DPI는 유지됩니다. 기본값으로 복원하려면 삭제 전에 [기본 DPI로 복원]을 선택하거나 `adb shell wm density reset`을 실행하십시오.</sub>

### DPI 적용 방식

<sub>`wm density N`은 내부적으로 `IWindowManager.setForcedDisplayDensityForUser(0, N, user)`를 호출합니다. AOSP의 `WindowManagerService`는 이 호출에 대해 `WRITE_SECURE_SETTINGS` 권한만 요구합니다(`@EnforcePermission(WRITE_SECURE_SETTINGS)`). 반면 `Settings.Secure.display_density_forced` 값은 WMS가 감시하지 않습니다. 따라서 이 값만 변경하는 경우 화면에 즉시 반영되지 않고, 사용자 전환이나 부팅 시점에야 반영됩니다. 본 앱은 이 방식을 사용하지 않습니다.</sub>

<sub>`DpiManager`는 아래 순서로 시도하며, 매 적용 후 값을 재조회하여 실제 변경 여부를 확인합니다.</sub>

1. <sub>**IWindowManager binder 호출(1순위).** `WindowManagerGlobal.getWindowManagerService()`로 획득한 객체에서 `setForcedDisplayDensityForUser`·`clearForcedDisplayDensityForUser`·`getInitialDisplayDensity`·`getBaseDisplayDensity`를 호출합니다. `wm density`와 동일한 경로를 앱 UID로 직접 호출하는 방식입니다. Android 9 이상의 hidden API 제한은 `HiddenApiBypass`를 이용해 `IWindowManager`/`WindowManagerGlobal`에 대해서만 해제합니다.</sub>
2. <sub>**`cmd window density N` → `wm density N` 프로세스 실행(대체 경로).** exit code·stdout·stderr를 로그에 기록하고, 실행 후 값을 재조회하여 성공 여부를 판정합니다.</sub>

<sub>현재 값은 `Physical density`(기기 기본값)와 `Override density`(강제값)로 표시되며, `wm density` 출력과 동일한 의미입니다.</sub>

### 디스플레이 번호별 적용

<sub>Samsung Galaxy Fold는 외부·내부 화면이 디스플레이 번호로 구분되어 있습니다(`wm density <dpi> -d 0`은 외부, `-d 1`은 내부로 알려져 있습니다). 앱은 `IWindowManager`에서 0~3번 디스플레이를 조회하고, 해상도로 식별 가능한 디스플레이에 `setForcedDisplayDensityForUser(id, …)`로 보정 DPI를 즉시 적용합니다. 이를 통해 접지 않아도 부팅 시 두 화면을 동시에 맞출 수 있습니다. 식별할 수 없는 디스플레이는 변경하지 않습니다. 화면이 꺼져 있으면 현재 값을 읽을 수 없는 경우가 있어, 이때는 "적용(확인 불가)"으로 기록하고 동일 값을 반복 적용하지 않습니다. ⋮ 메뉴 → 고급 정보에서 번호별 해상도·PPI·기본·현재·설정 DPI를 확인할 수 있습니다.</sub>

### 접기/펼치기

<sub>Galaxy Fold는 접고 펼칠 때 기본 display(0) 이면의 물리 화면이 전환됩니다. AOSP `DisplayWindowSettings`는 강제 density를 물리 화면(`DisplayInfo.uniqueId`)별로 저장합니다. 따라서 펼친 상태에서 맞춘 값이 접은 화면에는 적용되지 않을 수 있습니다. 부팅 직후에는 당시 활성화되어 있던 화면만 조정할 수 있습니다.</sub>

<sub>자동 적용을 활성화하면 `FoldWatchService`(foreground service)가 `DisplayManager.DisplayListener` 이벤트를 수신하여 확인하고, 두 화면이 모두 조정되면 자동으로 종료됩니다. 폴링 방식은 사용하지 않습니다. SM-F971N에서 DPI가 패널별로 개별 저장되는 것을 확인했습니다.</sub>

### 프로젝트 구조

<sub>[Android 앱 아키텍처 가이드](https://developer.android.com/topic/architecture)의 계층 구분(UI·domain·data)을 따릅니다. 의존 방향은 `ui`·`background` → `domain` → `data`입니다. 모든 계산은 domain 계층의 순수 함수에 위치하며, UI는 렌더링만 담당합니다.</sub>

```
app/src/main/java/com/labok/goat/
├── App.kt · AppInfo.kt          Application(크래시 기록·숨은 API 허용), 제품 이름
├── data/                        데이터 계층
│   ├── display/                 DpiController, DpiManager(IWindowManager, 디스플레이 ID별), PermissionReset(pm revoke 명령)
│   ├── shizuku/                 ShizukuAccess(상태·셸 권한 호출·WRITE_SECURE_SETTINGS 받기)
│   ├── appsize/                 앱별 크기: DensityShell(호출 창구), DensityServer(PC 셸 도우미), AppList·AppUsage,
│   │                            ForegroundWatcher(앞 화면 앱), AppDensityNotifier(알림창 조절)
│   ├── rotate/                  흔들어서 화면 회전: ShakeRotate(설정·회전 잠금), ShakeEngine(센서·자동 회전 잠깐 켜기),
│   │                            ShakeHaptics(흔든 리듬 진동)
│   ├── camera/                  CameraMute(촬영음 강제 설정)
│   ├── tweaks/                  RefreshRate · Resolution · ForceDark · Gos · Doze · Bloat · AnimationSpeed(Shizuku 셸·설정 쓰기)
│   ├── update/                  Updater(GitHub 릴리스 확인·내려받기·SHA-256 확인·Shizuku 또는 시스템 설치)
│   ├── settings/                SettingsRepository(외부 DPI·보정·자동·학습 화면·꺼짐 PPI·적용 기록·외부 변경)
│   └── log/                     LogRepository, PublicLogFile(Download/GOAT), CrashRecorder, DiagnosticReport
├── domain/                      판단·계산(단위 테스트 대상)
│   ├── DpiPolicy · ScreenPolicy  검증·반복 방지(자동만) / 외부·내부 판정(픽셀 수)·PPI 비율·적용 보류
│   ├── PanelSpec(DensityPlan)    PPI 결정(사양표 → 꺼짐 보고값 → 보고값), 목표 계산 일원화
│   ├── ScreenGeometry            화면 방향(내부 가로/세로 판정), 줄 수, dp → mm
│   ├── ExternalChange            외부 DPI 변경 판정, '이 값 기준' 역계산
│   ├── ScreenLearner             화면 학습(접는 중 중간값 차단)
│   ├── DpiFixer                  확인 → 적용 → 검증 공통 경로, 외부 변경 보류·해소
│   ├── AppDensityPolicy          앱별 배율 여섯 단계·한 단계 올리고 내리기
│   └── ShakeRotatePolicy         흔들기 판정(휘두름·탭·떨어뜨림·회전), 민감도·빠르기·자동 맞추기, 흔든 리듬 → 진동
├── background/                  BootReceiver · DpiWorker(부팅 재확인) · FoldWatchService(전환 감지)
│                                · GoatService(상주 서비스 하나: 알림창 앱 배율 조절 + 흔들기 감지, 알림 하나)
│                                · DensityWatchWorker(DPI 변경 감지) · ExternalChangeNotifier/Receiver(선택 알림)
└── ui/
    ├── liquid/                  공통 UI 컴포넌트(물방울 스위치·조절바·메뉴·로딩, FluidSim 입자 액체, LiquidTune 물방울 조절값)
    ├── text/Copy.kt             화면 문구 한곳(DPI·PPI·권한 등 표준 용어, 내부 구현 이름 금지 — 테스트로 강제)
    ├── art/                     그림: DeviceFrame(공통 기기 틀) 위에 폴드 기기·두 화면 미리보기·원리·연결·안내 장면
    ├── home/                    HomeScreen(상태·할 일·표시 크기·자동 적용·초기화), HomeViewModel, UiState
    ├── sizetest/                크기 테스트(세로 줄 수 비교, 줄 세기 화면, 기준 도형, mm 자)
    ├── help/                    사용 방법, 권한 설정 안내(GrantGuideSheet/Content)
    ├── shizuku/                 Shizuku 연결 단계 안내(ShizukuGuide)
    ├── appsize/                 앱별 화면 배율 설정(목록·사용 방법·권한 설정·초기화 시트, 알림 버튼 화면)
    ├── rotate/                  흔들어서 화면 회전(설정·고급 설정·사용 방법 그림·자동 맞추기 물방울 시트)
    ├── tweaks/                  주사율·해상도·다크 모드·GOS·잠자기 모드·기본 앱 정리·애니메이션 화면과 그림
    ├── camera/                  카메라 촬영음 끄기
    ├── nav/                     사이드바(물방울 선택·머리 물방울)
    ├── advanced/                고급 정보(디스플레이 ID·해상도·PPI·설정 DPI·적용 기록·로그)
    ├── about/                   앱 정보, 필요 권한(AppPermissions)
    └── components/              Gap(간격 가이드라인, 테스트로 강제), SheetColumn·HelpTopic, InfoRow, CommandBox
```

### 빌드

<sub>JDK 17과 Android SDK(compileSdk 35)가 있으면 Gradle로 빌드할 수 있습니다.</sub>

```bash
./gradlew assembleRelease testDebugUnitTest
```

<sub>`build-apk.sh`는 Android Studio 없이 CLI로 빌드하는 스크립트입니다. 필요한 JDK·SDK를 사용자 폴더(`$HOME/tools/android`, `GOAT_TOOLS`로 변경 가능)에 내려받아 사용하며, ARM64 Linux에서는 ARM64용 aapt2를 자동으로 사용합니다. x86_64 호스트에서는 `./build-apk.sh --docker`로 컨테이너 빌드도 가능합니다.</sub>

```bash
./build-apk.sh             # debug APK + 단위 테스트
./build-apk.sh --release   # release APK
./install-and-grant.sh app/build/outputs/apk/release/app-release.apk   # 설치 + 권한 부여(adb 연결 필요)
```

#### 테스트

- <sub>단위 테스트(118개): DPI 계산·PPI 결정·화면 학습·외부 변경 판정·반복 방지·화면 방향·흔들기 판정·주사율·해상도 비율·염소 표정·문구 및 간격 규칙 등을 검증합니다. `./gradlew testDebugUnitTest`</sub>
- <sub>화면 캡처: `./screenshots.sh`가 Robolectric과 Roborazzi를 사용하여 주요 화면을 PNG로 생성합니다(`app/build/outputs/roborazzi`).</sub>
- <sub>에뮬레이터 확인: 64비트 Android 컨테이너(redroid 등)에 설치하여 `adb shell wm size`/`wm density`로 접기·펼치기와 외부 변경을 시뮬레이션할 수 있습니다.</sub>

### 알려진 제약

- <sub>**실기기 확인 범위.** Galaxy Z Fold8(Android 17)에서 화면별 적용과 자동 적용을 확인했습니다. 외부 DPI 변경 감지·권한 초기화·크기 테스트는 Android 14 에뮬레이터에서 확인했습니다.</sub>
- <sub>**꺼진 화면의 외부 변경.** 꺼진 패널(-d 1)의 DPI는 `Settings.Secure`에 기록되지 않을 수 있어, 해당 화면의 외부 변경은 접거나 펼칠 때 감지됩니다(추정).</sub>
- <sub>**권한 회수는 PC에서.** WRITE_SECURE_SETTINGS는 권한 그룹이 없어 앱이 스스로 회수할 수 없습니다(`revokeSelfPermissionsOnKill` 거부 확인됨). 앱을 재설치해도 권한이 사라지므로, 업데이트 시에는 `-r`로 설치하십시오.</sub>
- <sub>**Hidden API 정책.** 향후 `HiddenApiBypass`가 차단되면 `cmd window density` 경로로 전환되며, 두 경로가 모두 차단되면 실패 로그를 기록합니다.</sub>
- <sub>**주사율 고정과 적응형 주사율.** One UI 6·7 일부 기기는 최고·최저 주사율을 동일하게 설정해도, 화면을 일정 시간 조작하지 않으면 디스플레이가 자체적으로 주사율을 낮춘다는 보고가 있습니다. GOAT는 이 동작을 차단하지 않습니다.</sub>
- <sub>**Direct Boot.** 잠금 해제 전에는 WorkManager를 사용할 수 없어 1회만 확인합니다.</sub>
- <sub>**서명.** release APK도 debug 키로 서명되므로 Play Protect 경고가 발생할 수 있습니다.</sub>

### 라이선스

<sub>Copyright (C) 2026 lala5bok</sub>

<sub>이 프로그램은 [GNU General Public License v3.0](LICENSE)(GPL-3.0)으로 배포되는 자유 소프트웨어입니다. 누구나 무료로 사용, 수정, 재배포할 수 있습니다. 다만 수정된 버전이나 이 코드를 포함한 프로그램을 배포할 때는 해당 소스코드도 GPL-3.0으로 공개해야 합니다.</sub>

<sub>GPL-3.0 조건(소스 공개)을 준수하기 어려운 회사나 제품에는 별도의 상용 라이선스를 제공합니다. 문의: lala5bok@gmail.com</sub>

<sub>이 앱이 사용하는 라이브러리 중 AndroidX·Jetpack Compose·HiddenApiBypass·Haze는 Apache License 2.0, Shizuku-API는 MIT License이며, 모두 GPL-3.0과 함께 사용할 수 있습니다.</sub>

# GOAT

**Galaxy Optimization Advanced Tool** — 갤럭시를 더 편하게 쓰게 해 주는 도구 모음

<sub>GOAT는 갤럭시에서 기본 설정만으로는 불편하거나 PC 명령이 필요했던 일을 폰 안에서 해결하는 앱입니다. ROOT 없이, 무료 앱 Shizuku나 PC의 adb로 권한을 처음 한 번만 주면 됩니다. 바꾼 설정은 재부팅 후에도 유지됩니다.</sub>

| <sub>메뉴</sub> | <sub>하는 일</sub> | <sub>필요한 것</sub> |
|---|---|---|
| <sub>**고급 화면 배율 설정**</sub> | <sub>폴드의 외부·내부 화면에서 글자와 아이콘이 같은 크기로 보이게 두 화면의 DPI를 맞춥니다. 접고 펴도, 재부팅해도 다시 맞춥니다. 폴드가 아니면 DPI 하나를 정합니다.</sub> | <sub>시스템 설정 권한(1회)</sub> |
| <sub>**앱별 화면 배율 설정**</sub> | <sub>삼성 '앱 화면 크게/작게'처럼 앱마다 화면 배율을 따로 정하고, 알림창에서 지금 앱의 배율을 바로 바꿉니다. 홈 화면 아이콘 크기만 따로 바꿀 수도 있습니다.</sub> | <sub>One UI 9 이상, Shizuku</sub> |
| <sub>**해상도 낮추기**</sub> | <sub>글자·아이콘 크기는 그대로 두고 해상도만 낮춥니다. 15초 안에 '유지'를 누르지 않으면 원래대로 돌아갑니다.</sub> | <sub>시스템 설정 권한(1회), 폴드 제외</sub> |
| <sub>**주사율 고정**</sub> | <sub>항상 최고 주사율(예: 120Hz) 또는 60Hz 절전으로 고정하고, 지금 주사율을 실시간으로 보여 줍니다.</sub> | <sub>Shizuku</sub> |
| <sub>**애니메이션 속도**</sub> | <sub>창·화면 전환·앱 안 움직임 속도를 한 번에(끔~1.5배) 또는 하나씩(0~2배) 바꿉니다.</sub> | <sub>시스템 설정 권한(1회)</sub> |
| <sub>**앱 다크 모드 강제**</sub> | <sub>다크 모드가 없는 앱도 시스템이 어둡게 그리게 합니다. 시스템 다크 모드도 함께 켜고, 끄면 원래대로 돌립니다.</sub> | <sub>Shizuku</sub> |
| <sub>**흔들어서 화면 회전**</sub> | <sub>평소에는 회전을 잠가 두고, 폰을 원하는 방향으로 든 채 흔들 때만 그 방향으로 돌린 뒤 다시 잠급니다. 큰 화면(펼친 폴드·태블릿)에서만 동작하게 할 수도 있습니다.</sub> | <sub>시스템 설정 권한(1회)</sub> |
| <sub>**카메라 촬영음 끄기**</sub> | <sub>국내 갤럭시의 강제 촬영음을 끄고 촬영음이 기기 소리 설정(무음·진동)을 따르게 합니다. 업데이트로 다시 켜지면 자동으로 다시 끕니다.</sub> | <sub>Shizuku(또는 PC adb 명령 한 번)</sub> |
| <sub>**기본 앱 정리**</sub> | <sub>쓰지 않는 삼성·제휴 기본 앱(빅스비·AR·게임 런처·페이스북 등)을 지우지 않고 꺼 둡니다. 스위치로 언제든 다시 켭니다.</sub> | <sub>Shizuku</sub> |
| <sub>**GOS 끄기**</sub> | <sub>게임 중 성능을 조절하는 게임 최적화 서비스를 끄고, 재부팅 뒤 다시 켜지면 다시 끕니다.</sub> | <sub>Shizuku</sub> |
| <sub>**잠자기 모드 끄기**</sub> | <sub>화면이 꺼진 동안 앱 동작을 몰아서 늦추는 잠자기 모드(Doze)를 꺼 알림이 늦게 오지 않게 합니다. 대기 배터리 소모는 늘어납니다.</sub> | <sub>Shizuku</sub> |

<sub>폴드는 외부 화면과 내부 화면의 픽셀 밀도(PPI)가 달라서, 같은 DPI를 써도 두 화면에서 글자와 아이콘의 실제 크기가 다르게 보입니다. `adb shell wm density`로 DPI를 바꿔도 재부팅하면 돌아가고 두 화면을 따로 맞추기도 번거롭습니다. 고급 화면 배율 설정은 외부 화면 DPI 하나만 정하면 내부 화면 DPI를 두 화면의 해상도와 PPI로 계산해 맞춥니다.</sub>

<p align="center">
  <img src="docs/screenshots/home.png" width="22%" alt="홈" />
  <img src="docs/screenshots/menu.png" width="22%" alt="메뉴" />
  <img src="docs/screenshots/help.png" width="22%" alt="사용 방법" />
  <img src="docs/screenshots/advanced.png" width="22%" alt="고급 정보" />
</p>

<sub>바뀐 점은 [변경 기록(CHANGELOG.md)](CHANGELOG.md)에 있습니다.</sub>

### adb 명령만 쓸 때와 비교

| | <sub>`adb shell wm density`만 쓸 때</sub> | <sub>GOAT</sub> |
|---|---|---|
| <sub>바꿀 때마다</sub> | <sub>PC를 연결하고 명령을 다시 입력</sub> | <sub>폰에서 슬라이더로 바로 조절</sub> |
| <sub>재부팅 후</sub> | <sub>기기 기본값으로 돌아가 다시 입력해야 함</sub> | <sub>부팅 뒤 자동으로 다시 적용</sub> |
| <sub>외부·내부 화면</sub> | <sub>같은 값을 넣으면 두 화면의 실제 크기가 다름. 값을 따로 계산해야 함</sub> | <sub>두 화면의 PPI 비율로 내부 DPI를 자동 계산</sub> |
| <sub>디스플레이 번호</sub> | <sub>접고 펼 때 `-d 0`/`-d 1`이 가리키는 화면이 바뀌어 헷갈림</sub> | <sub>해상도로 외부·내부를 알아보고 각각 적용</sub> |
| <sub>다른 설정에서 바뀌면</sub> | <sub>알 수 없음</sub> | <sub>감지해서 '이 값을 기준으로' 또는 '복원' 중 고르게 함</sub> |
| <sub>맞았는지 확인</sub> | <sub>눈대중</sub> | <sub>크기 테스트(두 화면의 줄 수, 카드로 맞춘 자)</sub> |
| <sub>되돌리기</sub> | <sub>`wm density reset` 명령을 기억해야 함</sub> | <sub>⋮ 메뉴 → 초기화 → 기본 DPI로 복원</sub> |
| <sub>PC가 필요한 때</sub> | <sub>매번</sub> | <sub>없음(Shizuku) 또는 처음 권한을 줄 때 한 번</sub> |

### 주요 기능

#### 고급 화면 배율 설정

- <sub>**외부 화면 기준 자동 계산**: 외부 화면 DPI만 정하면 내부 화면 DPI는 `외부 DPI × 내부 PPI ÷ 외부 PPI`로 계산합니다. 필요하면 내부 화면을 ±20 범위로 보정할 수 있습니다.</sub>
- <sub>**두 화면에 각각 적용**: 폴드는 화면(디스플레이 ID)마다 DPI를 따로 저장하므로, 확인된 두 화면에 각각 적용합니다.</sub>
- <sub>**자동 적용**: 부팅 뒤(0·5·15·30·60초 확인), 접기/펼치기 뒤, 다른 앱이 DPI를 바꿨을 때 다시 확인합니다. 감지는 이벤트 방식이라 상주하거나 주기적으로 깨어나지 않습니다.</sub>
- <sub>**외부 DPI 변경 감지**: 시스템 설정의 화면 크기 등으로 DPI가 바뀌면 덮어쓰지 않고, 그 값을 기준으로 할지 이전 값으로 복원할지 알림과 홈 카드로 묻습니다.</sub>
- <sub>**크기 테스트**: 두 화면은 실제 높이가 거의 같아서, 크기가 맞으면 한 화면에 들어가는 줄 수도 같습니다. 전체 화면에 번호 매긴 줄을 채워 접고 펴며 비교할 수 있고, 카드(짧은 변 54 mm)로 맞춘 자로 실제 길이도 잴 수 있습니다.</sub>
- <sub>**권한 설정 안내**: 연결 방식(USB/무선)과 PC 운영체제(Windows/Mac/Linux)에 맞춘 단계별 안내, 명령 복사, 자주 나오는 오류 해결 방법을 앱 안에서 보여 줍니다. 안내문을 PC로 보내거나 메일로 보낼 수도 있습니다.</sub>

#### 앱별 화면 배율 설정

- <sub>삼성 '앱 화면 크게/작게'와 같은 앱별 크기를 GOAT에서 바꿉니다(One UI 9 이상, Shizuku 필요).</sub>
- <sub>최근 사용 순 목록, 이미 바꾼 앱 모아 보기, 앱 이름 검색, 한 번에 기본으로 되돌리기를 지원합니다.</sub>
- <sub>알림창의 [작게]·[기본]·[크게]로 지금 보고 있는 앱(GOAT 자신 포함)의 배율을 바로 바꿉니다.</sub>

#### 해상도 낮추기

- <sub>기본 화면의 강제 해상도(`wm size`와 같은 호출)를 FHD+·900·HD+ 또는 85%·75%로 낮추고, 표시 밀도도 같은 비율로 줄여 글자·아이콘 크기를 유지합니다. 고급 화면 배율 설정을 쓰고 있으면 그 목표도 같은 비율로 맞춰 서로 덮어쓰지 않습니다.</sub>
- <sub>바꾼 뒤 15초 안에 '유지'를 누르지 않으면 되돌립니다(앱 카운트다운, 예약 작업, 잠금 해제 전 부팅 수신기의 세 겹). 되돌릴 때는 GOAT가 바꾸기 직전 해상도(삼성 설정의 FHD+ 포함)와 밀도로 돌아갑니다. 폴드는 지원하지 않습니다.</sub>

#### 주사율 고정

- <sub>안드로이드 공통 설정 `peak_refresh_rate`·`min_refresh_rate`와 삼성 `refresh_rate_mode`(삼성 기기만)를 Shizuku 셸로 한 번에 바꿉니다. '항상 최고'(예: 항상 120Hz), '60Hz 절전', '시스템 기본'(처음 값으로 되돌리고 없던 키는 지움) 중에서 고르고, 지금 화면 주사율을 실시간으로 보여 줍니다.</sub>
- <sub>앱 밖에서 바꾼 값은 덮어쓰지 않습니다. 최고 60Hz인 화면은 바꿀 것이 없다고 알립니다.</sub>

#### 애니메이션 속도

- <sub>개발자 옵션의 창·전환·Animator 배율(`settings global`)을 바꿉니다. 커뮤니티에서 가장 많이 쓰는 0.5배를 한 번에 고를 수 있고, '하나씩 맞추기'로 0.05배 단위 조절도 됩니다.</sub>

#### 앱 다크 모드 강제

- <sub>개발자 옵션 '강제 다크 모드 재정의'와 같은 `debug.hwui.force_dark`를 Shizuku `setprop`으로 켭니다. 안드로이드는 시스템 다크 모드일 때만 이 값을 보므로, 켤 때 다크 모드가 꺼져 있으면 함께 켜고(`cmd uimode night yes`) 끌 때 그 전 방식(꺼짐·자동·예약 시각)으로 되돌립니다. 안드로이드 16 이상은 '더 많은 앱을 어둡게'(`accessibility_force_invert_color_enabled`)도 함께 켭니다.</sub>
- <sub>재부팅하면 지워지는 값이라 부팅마다 한 번 다시 켭니다(같은 부팅 안의 직접 변경은 존중). 앱별 선택은 공식 경로가 없어 모든 앱에 적용됩니다.</sub>
- <sub>시스템이 색을 자동으로 바꾸는 방식이라 일부 앱은 글자가 안 보이거나 흰 네모가 생길 수 있습니다. 화면과 사용 방법에서 미리 안내합니다.</sub>

#### 흔들어서 화면 회전

- <sub>폰을 원하는 방향으로 든 채 흔들면 그 순간만 자동 회전을 켜 든 방향대로 돌리고 다시 잠급니다. 세로로 든 채 흔들면 세로 그대로입니다.</sub>
- <sub>톡 친 것·부딪힌 것·떨어뜨린 것·걷기·차 진동은 거르고, 빠르게 휙휙 흔드는 것과 천천히 크게 흔드는 것은 모두 인식합니다.</sub>
- <sub>민감도(1~30)와 고급 설정(흔드는 횟수·빠르기·손목 회전 기준·탭 거르기·큰 화면에서만), 4초 동안 흔들어 이 값들을 한 번에 맞추는 자동 맞추기를 지원합니다.</sub>
- <sub>'큰 화면에서만 회전'을 켜면 짧은 변이 600dp 이상인 화면(펼친 폴드·태블릿)에서만 돌리고, 접은 화면에서는 흔들어도 돌리지 않습니다.</sub>
- <sub>자동 맞추기 화면의 물방울은 흔들림·충격이 잡힐 때마다 튀어서 판정을 눈으로 보여 줍니다. 물의 양·점도·표면장력·흩어짐·튀는 세기·중력·출렁임·가운데로 돌아오는 힘을 조절바로 바꿀 수 있습니다(판정에는 영향 없음).</sub>
- <sub>돌아가면 흔든 리듬 그대로 진동합니다(휙 휘힉 → 톡 토독). 진동 세기는 0~10으로 조절합니다.</sub>

#### 카메라 촬영음 끄기

- <sub>숨은 시스템 설정 `csc_pref_camera_forced_shuttersound_key`를 0으로 바꿔, 무음·진동 모드에서는 소리 없이 찍히게 합니다(소리 모드에서는 그대로 납니다). 일반 앱 권한으로는 바꿀 수 없어 Shizuku 셸 권한으로 `settings put system`을 실행하고, Shizuku가 없으면 PC 명령(`adb shell settings put system csc_pref_camera_forced_shuttersound_key 0`)을 안내합니다.</sub>
- <sub>업데이트 뒤 1로 돌아오는 기기가 있어, '다시 켜지면 자동으로 끄기'를 켜 두면 Shizuku가 연결될 때마다 확인해 다시 끕니다.</sub>
- <sub>촬영음은 몰래 찍기를 막는 장치입니다. 다른 사람을 동의 없이 찍는 것은 처벌받으며, 앱 안에도 이 안내를 둡니다.</sub>

#### 기본 앱 정리

- <sub>`pm disable-user --user 0`으로 끄고 `pm enable`로 되돌립니다(삭제하지 않음). 꺼도 전화·문자·설정·카메라가 멈추지 않는 앱만 목록에 넣었고, 삼성페이·클라우드처럼 영향이 있는 앱은 '주의'로 표시해 한 번 더 묻습니다.</sub>

#### GOS 끄기

- <sub>게임 최적화 서비스(`com.samsung.android.game.gos`, 이전 `com.enhance.gameservice`)를 기본 앱 정리와 같은 `pm disable-user`로 끕니다. 두 화면이 같은 상태를 씁니다. 게임 런처·게임 부스터는 건드리지 않습니다.</sub>
- <sub>재부팅 뒤 다시 켜지는 기기를 위해 부팅마다 한 번 Shizuku 연결 때 다시 끕니다.</sub>

#### 잠자기 모드 끄기

- <sub>`dumpsys deviceidle disable`(깊은·가벼운 잠자기 모두)을 Shizuku 셸로 실행하고, `dumpsys deviceidle enabled deep|light`로 다시 읽어 확인합니다. 끌 때 배터리 영향을 한 번 더 묻습니다.</sub>
- <sub>메모리에만 있는 설정이라 재부팅하면 다시 켜집니다. 꺼 두었다면 부팅마다 한 번 Shizuku가 연결될 때 다시 끄고, 같은 부팅 안에서 PC 등으로 직접 켠 것은 그대로 둡니다.</sub>
- <sub>특정 앱의 알림만 늦다면 그 앱의 배터리 설정을 '제한 없음'으로 두는 편이 배터리에 낫다고 화면에서 안내합니다.</sub>

#### 앱 안 업데이트

- <sub>홈을 열 때(앱을 새로 켜거나 6시간이 지났을 때) GitHub 릴리스의 최신 버전(`releases/latest`)을 확인하고, 이 앱보다 새로우면 홈 위쪽에 [지금 업데이트] 카드를 띄웁니다. 앱 설정 → 업데이트에서 직접 확인할 수도 있습니다.</sub>
- <sub>APK를 앱 캐시에 내려받아 크기와 릴리스 노트의 SHA-256이 맞을 때만 설치합니다. Shizuku가 연결돼 있으면 `pm install -r`로 묻지 않고 설치하고(끝나면 GOAT가 다시 시작됩니다), 없으면 시스템 설치 창을 띄웁니다(처음 한 번 'GOAT의 앱 설치 허용' 필요).</sub>
- <sub>같은 키로 서명한 판만 설치되므로, 다른 곳에서 받은 APK로 바뀌지 않습니다.</sub>

#### 공통

- <sub>**문제 신고**: 진단 파일(과 기록 파일을 켰다면 오늘 기록)을 첨부한 메일을 바로 작성합니다.</sub>
- <sub>**앱 설정**(사이드바 맨 아래): '설정'에 업데이트 확인, UI 유연성(움직일 때 출렁이는 정도 0~150%, 속도는 그대로), 햅틱 세기(0~150%), 기록 파일 남기기를, '정보'에 앱 정보·변경 기록·필요 권한을 모았습니다.</sub>
- <sub>**기록 파일 남기기**(앱 설정, 기본 꺼짐): 켜면 기능마다 남기는 작동 기록을 `다운로드/GOAT/goat-날짜-log.txt`에 날짜별로 저장합니다. 끄면 다운로드 폴더에 파일을 만들지 않고, 앱 안에만 짧게 남깁니다.</sub>
- <sub>**화면 구성**: 모든 기능 화면은 소개 → 권한·연결 → 주 조작 → 세부 옵션 → 고급 설정 → 주의 순서이고, ⋮ 메뉴는 사용 방법 → 권한 설정 → 문제 신고 → 초기화(구분선 아래) 순서입니다.</sub>

### 호환 기기

#### 기능별 호환(폴드가 아닌 갤럭시·One UI 버전)

<sub>코드가 실행 시점에 기기를 확인해, 쓸 수 없는 기기에서는 화면에 '이 기기에서는 쓸 수 없습니다' 같은 안내를 보여 줍니다. 아래 표는 코드 확인과 공개 자료 조사 결과이고, 실기기 확인은 Galaxy Z Fold8(One UI 9)뿐입니다.</sub>

| <sub>기능</sub> | <sub>S·A·Flip</sub> | <sub>Tab</sub> | <sub>One UI 4~8 (Android 12~16)</sub> | <sub>판정 방법</sub> |
|---|---|---|---|---|
| <sub>고급 화면 배율 설정</sub> | <sub>지원(DPI 하나)</sub> | <sub>지원</sub> | <sub>지원(AOSP 표준 호출)</sub> | <sub>두 화면 맞추기만 힌지 센서로 폴드 판정</sub> |
| <sub>앱별 화면 배율 설정</sub> | <sub>One UI 9 이상</sub> | <sub>One UI 9 이상</sub> | <sub>안 됨(삼성 '앱 화면 크게/작게'가 One UI 9에서 생김)</sub> | <sub>시스템 함수가 있는지 확인</sub> |
| <sub>해상도 낮추기</sub> | <sub>지원(A 시리즈 포함)</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>폴드만 제외</sub> |
| <sub>주사율 고정</sub> | <sub>60Hz보다 빠른 화면만</sub> | <sub>같음</sub> | <sub>지원(One UI 6·7은 적응형 주사율이 잠깐 낮출 수 있음)</sub> | <sub>지원 주사율 목록</sub> |
| <sub>애니메이션 속도</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>없음(표준 설정)</sub> |
| <sub>앱 다크 모드 강제</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>지원(Android 16부터 '더 많은 앱 어둡게'도 함께)</sub> | <sub>없음</sub> |
| <sub>흔들어서 화면 회전</sub> | <sub>지원</sub> | <sub>지원('큰 화면에서만'으로 구분)</sub> | <sub>지원</sub> | <sub>가속도 센서</sub> |
| <sub>카메라 촬영음 끄기</sub> | <sub>국내·일본 모델만</sub> | <sub>같음</sub> | <sub>버전 무관(국가 설정에 따름)</sub> | <sub>강제 설정 값이 있는지 확인</sub> |
| <sub>기본 앱 정리</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>설치된 앱만 목록에 표시</sub> |
| <sub>GOS 끄기</sub> | <sub>지원</sub> | <sub>GOS가 없는 탭은 안내만</sub> | <sub>지원</sub> | <sub>GOS 패키지 설치 여부</sub> |
| <sub>잠자기 모드 끄기</sub> | <sub>지원</sub> | <sub>지원</sub> | <sub>지원(Android 6부터 있는 표준 기능)</sub> | <sub>없음</sub> |


<sub>Android 12(API 31) 이상이 필요합니다. 고급 화면 배율 설정의 두 화면 맞추기는 Galaxy Z Fold 시리즈, 앱별 화면 배율 설정은 One UI 9 이상의 갤럭시가 대상입니다. 해상도 낮추기는 폴드를 지원하지 않고, 주사율 고정은 60Hz보다 빠른 화면에서만 의미가 있습니다. 흔들어서 화면 회전은 가속도 센서가 있는 기기면 됩니다. 아래 표는 고급 화면 배율 설정 기준입니다.</sub>

| <sub>기기</sub> | <sub>상태</sub> | <sub>비고</sub> |
|---|---|---|
| <sub>Galaxy Z Fold8 (SM-F971N)</sub> | <sub>실기기 확인</sub> | <sub>화면별 적용, 자동 적용, 크기 일치 확인</sub> |
| <sub>Galaxy Z Fold7</sub> | <sub>지원(실기기 미확인)</sub> | <sub>제조사 패널 사양으로 PPI 보정</sub> |
| <sub>Galaxy Z Fold6</sub> | <sub>지원(실기기 미확인)</sub> | <sub>제조사 패널 사양으로 PPI 보정</sub> |
| <sub>Galaxy Z Fold4 · Fold5</sub> | <sub>지원(실기기 미확인)</sub> | <sub>제조사 패널 사양으로 PPI 보정</sub> |
| <sub>Galaxy Z Fold3</sub> | <sub>지원(실기기 미확인)</sub> | <sub>제조사 패널 사양으로 PPI 보정</sub> |
| <sub>Galaxy Z Fold2 (Android 12 이상)</sub> | <sub>동작 예상</sub> | <sub>사양표에 없어 기기가 알려 준 PPI로 계산</sub> |
| <sub>그 밖의 폴더블(Pixel Fold 등)</sub> | <sub>동작 예상</sub> | <sub>기기가 알려 준 PPI로 계산. 화면별 저장 방식이 다르면 접기/펼치기 때 다시 적용</sub> |
| <sub>Galaxy Z Flip 시리즈</sub> | <sub>대상 아님</sub> | <sub>외부 화면이 보조 화면이라 맞출 필요가 거의 없음</sub> |
| <sub>Android 11 이하</sub> | <sub>지원 안 함</sub> | <sub>minSdk 31</sub> |

<sub>기기가 알려 주는 PPI가 실제 패널과 다를 수 있어, 사양표에 있는 기종은 제조사 사양(해상도·대각선)으로 PPI를 다시 계산합니다. 확인된 기기가 늘면 이 표에 추가합니다. 다른 기기에서 써 보셨다면 앱의 ⋮ 메뉴 → 문제 신고로 진단 파일을 보내 주세요.</sub>

### 설치

1. <sub>[Releases](../../releases/latest)에서 최신 APK를 내려받아 설치합니다(출처를 알 수 없는 앱 설치 허용 필요).</sub>
2. <sub>앱을 열면 **권한 설정 필요** 카드가 보입니다. [설정 방법]을 누르고 연결 방법을 고릅니다.</sub>
   - <sub>**Shizuku**(PC 없이): 무료 앱 [Shizuku](https://shizuku.rikka.app/)를 설치하고, 안내에 따라 무선 디버깅으로 페어링·시작한 뒤 GOAT를 허용하면 GOAT가 스스로 권한을 받습니다. Wi-Fi가 필요하며, 권한을 받은 뒤에는 Shizuku를 꺼도 됩니다.</sub>
   - <sub>**PC 무선** / **PC 유선**: PC 운영체제에 맞춘 안내대로 PC에서 아래 명령을 한 번 실행합니다.</sub>

```bash
adb shell pm grant com.labok.goat android.permission.WRITE_SECURE_SETTINGS
```

<sub>이 권한은 일반 권한 창으로는 받을 수 없어 셸 권한(Shizuku 또는 adb)이 필요합니다. 재부팅해도 유지되며, 권한을 준 뒤에는 USB/무선 디버깅을 꺼도 됩니다. 앱을 삭제하면 권한도 사라지므로, 업데이트는 삭제하지 말고 덮어 설치(`adb install -r` 또는 APK 실행)하세요.</sub>

#### 안 될 때

| <sub>PC에 나온 문구</sub> | <sub>해결</sub> |
|---|---|
| <sub>`protocol fault (couldn't read status message)`</sub> | <sub>폰의 VPN을 끄고, 같은 와이파이인지 확인한 뒤 `adb kill-server` → `adb start-server`. 그래도 안 되면 폰과 PC를 재시작</sub> |
| <sub>`more than one device/emulator`</sub> | <sub>`adb devices`로 목록을 보고 `adb -s <이름> shell pm grant …`처럼 기기를 지정</sub> |
| <sub>`unauthorized`</sub> | <sub>폰의 'USB 디버깅 허용' 창에서 허용. 안 보이면 개발자 옵션 → USB 디버깅 권한 승인 취소 후 다시 연결</sub> |
| <sub>`'adb' is not recognized`</sub> | <sub>Windows는 platform-tools 폴더에서 `.\adb`로 실행</sub> |

<sub>무선 페어링에는 adb 30 이상이 필요하고, `adb pair`(페어링 팝업의 포트)와 `adb connect`(무선 디버깅 첫 화면의 포트)는 포트가 다릅니다.</sub>

### 사용

1. <sub>**표시 크기**에서 외부 화면 DPI를 정합니다. 값이 작을수록 글자와 아이콘이 작게(화면이 넓게) 표시됩니다. 내부 화면 DPI는 자동으로 계산되고, 미리보기에서 두 화면의 크기를 비교할 수 있습니다.</sub>
2. <sub>한 번 접었다 펴서 두 화면의 정보를 모으면 두 화면에 모두 적용됩니다.</sub>
3. <sub>**자동 적용** 스위치를 켜 두면 재부팅·화면 전환·외부 변경 때 알아서 다시 맞춥니다.</sub>
4. <sub>크기가 미세하게 다르게 느껴지면 **크기 테스트**로 확인하고 **내부 화면 보정**으로 다듬습니다.</sub>

<sub>앱을 열면 **홈**에서 모든 기능의 지금 상태를 타일(아이콘 오른쪽에 이름과 상태, 켜진 기능은 굵은 테두리)로 보고 바로 들어갑니다. 사이드바(왼쪽 위 ☰)는 **화면 배율**(고급 화면 배율 설정·앱별 화면 배율 설정), **화면 표시**(해상도 낮추기·주사율 고정·애니메이션 속도·앱 다크 모드 강제), **편의 기능**(흔들어서 화면 회전·카메라 촬영음 끄기), **시스템**(기본 앱 정리·GOS 끄기·잠자기 모드 끄기)으로 묶여 있고 맨 아래에 **앱 설정**이 있으며, 폴드 내부 화면·태블릿처럼 넓은 화면에서는 늘 왼쪽에 펼쳐져 있습니다. 뒤로 가기는 사이드바 닫기 → 홈 → 앱 닫기 순서입니다. 각 기능의 세부 도구(사용 방법·권한 설정·문제 신고·초기화)는 오른쪽 위 ⋮ 메뉴에 같은 순서로 있습니다. 표시 크기 조절바는 값만 바꾸고 [적용]을 눌러야 반영됩니다. 조절바를 붙들고 0.75초 가만히 있으면 앞뒤 10칸을 세밀하게 고르는 확대 패널이 열립니다. 사이드바나 팝업이 열리면 뒤 화면이 흐려집니다.</sub>

#### 앱별 화면 배율 설정

- <sub>**필요한 것**: 삼성 One UI 9 이상과 Shizuku. 삼성은 앱별 크기를 바꾸는 시스템 함수(`IActivityTaskManager.setUserCustomDensity`)에 시스템 전용 권한을 걸어 두어, Shizuku가 빌려 주는 셸 권한으로 부릅니다. Shizuku가 없으면 PC에서 명령 한 번으로 GOAT 셸 도우미를 켜서 쓸 수도 있습니다.</sub>
- <sub>**Shizuku가 필요한 때**: 값을 바꿀 때만입니다. 정해 둔 크기는 시스템에 저장돼 Shizuku가 꺼져 있어도, 재부팅해도 유지됩니다.</sub>
- <sub>**여섯 단계**: 시스템이 320·360·420·450·480·510만 받습니다. '기본'은 앱별 설정을 지웁니다.</sub>
- <sub>**접고 펼 때**: 앱별 크기는 하나의 고정 DPI라 두 화면에 같은 숫자가 쓰여, 픽셀 밀도가 다른 두 화면에서 실제 크기가 다르게 보입니다.</sub>
- <sub>**알림창 조절**: 켜 두면 알림에 지금 보고 있는 앱과 크기가 나옵니다. 앞 화면 앱은 Shizuku 셸 권한으로 등록한 작업 스택 감시(ITaskStackListener)로 알아내므로 주기적으로 확인하지 않습니다. 크기를 바꾸면 삼성 동작대로 그 앱이 다시 시작되고, GOAT가 다시 열어 줍니다.</sub>

#### 흔들어서 화면 회전

- <sub>**동작**: 흔들면 시스템 자동 회전을 잠깐 켜고(`accelerometer_rotation`), 화면이 돌아가면 그 방향을 `user_rotation`에 넣어 다시 잠급니다. 세로로 든 채 흔들면 세로 그대로입니다. 권한은 고급 화면 배율 설정과 같은 `WRITE_SECURE_SETTINGS`입니다.</sub>
- <sub>**흔들기 판정**: 저역 통과로 중력을 빼고, 움직이는 방향이 뒤집힐 때마다 휘두름 하나로 셉니다. 휘두름마다 세기·길이·날카로움·손목 회전을 기록해, 한 샘플짜리 탭·충격과 떨어뜨림(자유 낙하)은 거르고, 손목이 돌았거나 충분히 세게 흔든 것만 셉니다. 근접 센서가 가려져 있으면(주머니 속) 무시하고, 화면이 꺼지면 센서를 쓰지 않습니다.</sub>
- <sub>**설정**: 민감도(1~30)와 고급 설정(흔드는 횟수 2~4, 빠르기, 손목 회전 기준, 탭 거르기, 큰 화면에서만). '흔들어서 자동으로 맞추기'는 4초 동안 흔든 세기·리듬·손목 회전으로 이 값들을 함께 맞춥니다.</sub>
- <sub>**진동**: 흔든 리듬 그대로 울립니다. 진동 사이 최소 50ms, 세기는 로그 곡선의 세 단계, 가장 센 휘두름은 클릭·나머지는 틱(Android 햅틱 지침). 세기 0~10 조절바를 움직이면 예시가 울립니다.</sub>
- <sub>**알림 하나**: 안드로이드는 알림이 없는 앱의 센서를 멈추므로 GOAT 상주 서비스(`GoatService`)가 알림 하나를 띄워 둡니다. 알림창 앱별 배율 조절을 켜 두었다면 그 알림 하나를 같이 쓰고, 흔들기만 켰을 때는 소리·진동 없는 조용한 알림 하나만 둡니다. 접근성 서비스는 쓰지 않습니다(Play 프로텍트가 웹에서 받은 접근성 앱의 설치를 막기 때문).</sub>

#### 외부 DPI 변경 처리

<sub>앱은 화면마다 마지막으로 적용한 DPI를 기억합니다. 시스템 설정의 화면 크기, 다른 앱, adb 등에서 값이 바뀌면 자동으로 덮어쓰지 않고, 홈 맨 위 카드와 알림에서 **이 값 기준으로 설정** 또는 **이전 값으로 복원**을 고르게 합니다. 감지는 WorkManager의 콘텐츠 URI 트리거(`Settings.Secure.display_density_forced`)라 상주 프로세스나 주기 확인이 없습니다. 부팅 직후 기기가 기본값으로 되돌린 경우는 외부 변경이 아니라 복원 대상입니다.</sub>

#### 안전장치

- <sub>범위(120~640)를 벗어난 값은 저장하지 않고, 조절바의 최대는 화면의 짧은 변이 320dp 이상 남는 값(짧은 변 px × 160 ÷ 320)으로 기기마다 정합니다.</sub>
- <sub>현재 값이 목표와 같으면 다시 적용하지 않습니다.</sub>
- <sub>자동 적용은 1분에 최대 5회입니다(시스템과 서로 덮어쓰는 무한 반복 방지). 사용자가 직접 바꾸는 것은 제한하지 않습니다.</sub>
- <sub>보정 비율이 0.7~1.4를 벗어나거나 결과가 범위를 넘으면 적용을 보류합니다.</sub>
- <sub>부팅 재확인은 unique work(`dpi_recover`, REPLACE)라 작업이 겹치지 않습니다.</sub>

#### 개인정보와 권한

<sub>저장소·위치 권한을 쓰지 않습니다. 인터넷은 업데이트 확인에만 써서 GitHub 릴리스 정보와 APK만 받고, 어떤 데이터도 외부로 보내지 않습니다. 앱별 화면 배율 설정은 앱 목록과 최근 사용 기록을 기기 안에서만 읽어 목록을 정렬하고 지금 앱을 찾습니다. 흔들어서 화면 회전은 화면이 켜져 있을 때만 가속도·자이로·근접 센서를 읽고, 그 값을 저장하거나 보내지 않습니다. 로그는 기본으로 앱 안에만 짧게 남고, 앱 설정의 '기록 파일 남기기'를 켰을 때만 `다운로드/GOAT/`에 파일로 저장됩니다. 로그와 진단 파일은 사용자가 문제 신고로 직접 보낼 때만 메일에 첨부됩니다. 쓰는 권한 전체와 이유는 앱 정보 화면에 있습니다.</sub>

### DPI 복구

<sub>화면이 너무 작거나 커서 앱을 조작하기 어려우면 PC에서 다음 명령을 실행하십시오.</sub>

```bash
adb shell wm density reset
```

<sub>ADB 없이 복구하려면 앱의 ⋮ 메뉴 → 초기화 → [기본 DPI로 복원]을 누르거나, 설정 → 디스플레이 → 화면 크게/작게를 쓰십시오.</sub>

### 삭제

```bash
adb uninstall com.labok.goat
```

<sub>단말의 설정 → 애플리케이션에서 삭제해도 됩니다. 삭제한 뒤에도 마지막으로 적용한 강제 DPI는 남아 있습니다. 기본값으로 돌리려면 삭제 전에 [기본 DPI로 복원]을 누르거나 `adb shell wm density reset`을 실행하십시오.</sub>

### DPI 적용 방식

<sub>`wm density N`은 내부적으로 `IWindowManager.setForcedDisplayDensityForUser(0, N, user)`를 호출합니다. AOSP의 `WindowManagerService`는 이 호출에 `WRITE_SECURE_SETTINGS` 권한만 요구합니다(`@EnforcePermission(WRITE_SECURE_SETTINGS)`). 반면 `Settings.Secure.display_density_forced` 값은 WMS가 감시하지 않습니다. 그래서 이 값만 쓰면 화면에 바로 반영되지 않고, 사용자 전환이나 부팅 때에야 읽힙니다. 이 앱은 그 방식을 쓰지 않습니다.</sub>

<sub>`DpiManager`는 아래 순서로 시도하고, 매번 적용한 뒤 값을 다시 읽어 실제로 바뀌었는지 확인합니다.</sub>

1. <sub>**IWindowManager binder 호출(1순위).** `WindowManagerGlobal.getWindowManagerService()`로 얻은 객체에서 `setForcedDisplayDensityForUser`·`clearForcedDisplayDensityForUser`·`getInitialDisplayDensity`·`getBaseDisplayDensity`를 호출합니다. `wm density`와 같은 경로를 앱 UID로 직접 부르는 방식입니다. Android 9 이상의 hidden API 제한은 `HiddenApiBypass`로 `IWindowManager`/`WindowManagerGlobal`에 대해서만 해제합니다.</sub>
2. <sub>**`cmd window density N` → `wm density N` 프로세스 실행(대체 경로).** exit code·stdout·stderr를 로그에 남기고, 실행 뒤 값을 다시 읽어 성공 여부를 판정합니다.</sub>

<sub>현재 값은 `Physical density`(기기 기본값)와 `Override density`(강제값)로 보여 주며, `wm density` 출력과 같은 의미입니다.</sub>

### 디스플레이 번호별 적용

<sub>Samsung Galaxy Fold는 외부·내부 화면이 디스플레이 번호로 따로 있습니다(`wm density <dpi> -d 0`은 외부, `-d 1`은 내부로 알려져 있습니다). 앱은 `IWindowManager`에서 0~3번 디스플레이를 읽고, 해상도로 어느 화면인지 확인되는 디스플레이에 `setForcedDisplayDensityForUser(id, …)`로 보정 DPI를 바로 적용합니다. 그래서 접지 않아도 부팅 때 두 화면을 한꺼번에 맞출 수 있습니다. 어느 화면인지 확인할 수 없는 디스플레이는 건드리지 않습니다. 꺼진 화면은 현재 값을 읽을 수 없을 수 있어서, 이때는 "적용(확인 불가)"으로 기록하고 같은 값을 반복 적용하지 않습니다. ⋮ 메뉴 → 고급 정보에서 번호별 해상도·PPI·기본·현재·설정 DPI를 확인할 수 있습니다.</sub>

### 접기/펼치기

<sub>Galaxy Fold는 접고 펼칠 때 기본 display(0) 뒤의 물리 화면이 바뀝니다. AOSP `DisplayWindowSettings`는 강제 density를 물리 화면(`DisplayInfo.uniqueId`)별로 저장합니다. 그래서 펼친 상태에서 맞춘 값이 접은 화면에는 적용되지 않을 수 있습니다. 부팅 직후에는 그때 켜져 있던 화면만 고칠 수 있습니다.</sub>

<sub>자동 적용을 켜면 `FoldWatchService`(foreground service)가 `DisplayManager.DisplayListener` 이벤트를 받아 확인하고, 두 화면이 모두 맞춰지면 스스로 멈춥니다. 폴링은 하지 않습니다. SM-F971N에서 DPI가 패널별로 따로 저장되는 것을 확인했습니다.</sub>

### 프로젝트 구조

<sub>[Android 앱 아키텍처 가이드](https://developer.android.com/topic/architecture)의 계층 구분(UI · domain · data)을 따릅니다. 의존 방향은 `ui`·`background` → `domain` → `data`입니다. 계산은 모두 domain의 순수 함수에 두고 UI는 그리기만 합니다.</sub>

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

<sub>JDK 17과 Android SDK(compileSdk 35)가 있으면 Gradle로 빌드합니다.</sub>

```bash
./gradlew assembleRelease testDebugUnitTest
```

<sub>`build-apk.sh`는 Android Studio 없이 CLI로 빌드하는 스크립트입니다. 필요한 JDK·SDK를 사용자 폴더(`$HOME/tools/android`, `GOAT_TOOLS`로 변경 가능)에 받아 쓰며, ARM64 Linux에서는 ARM64용 aapt2를 자동으로 씁니다. x86_64 호스트에서는 `./build-apk.sh --docker`로 컨테이너 빌드도 할 수 있습니다.</sub>

```bash
./build-apk.sh             # debug APK + 단위 테스트
./build-apk.sh --release   # release APK
./install-and-grant.sh app/build/outputs/apk/release/app-release.apk   # 설치 + 권한 부여(adb 연결 필요)
```

#### 테스트

- <sub>단위 테스트(118개): DPI 계산·PPI 결정·화면 학습·외부 변경 판정·반복 방지·화면 방향·흔들기 판정·주사율·해상도 비율·염소 표정·문구와 간격 규칙 등. `./gradlew testDebugUnitTest`</sub>
- <sub>화면 캡처: `./screenshots.sh`가 Robolectric과 Roborazzi로 주요 화면을 PNG로 만듭니다(`app/build/outputs/roborazzi`).</sub>
- <sub>에뮬레이터 확인: 64비트 Android 컨테이너(redroid 등)에 설치해 `adb shell wm size`/`wm density`로 접기·펼치기와 외부 변경을 흉내 낼 수 있습니다.</sub>

### 알려진 제약

- <sub>**실기기 확인 범위.** Galaxy Z Fold8(Android 17)에서 화면별 적용과 자동 적용을 확인했습니다. 외부 DPI 변경 감지·권한 초기화·크기 테스트는 Android 14 에뮬레이터에서 확인했습니다.</sub>
- <sub>**꺼진 화면의 외부 변경.** 꺼진 패널(-d 1)의 DPI는 `Settings.Secure`에 기록되지 않을 수 있어, 그 화면의 외부 변경은 접거나 펼 때 감지됩니다(추정).</sub>
- <sub>**권한 회수는 PC에서.** WRITE_SECURE_SETTINGS는 권한 그룹이 없어 앱이 스스로 회수할 수 없습니다(`revokeSelfPermissionsOnKill` 거부 확인). 앱을 다시 설치해도 사라지므로 업데이트는 `-r`로 설치하십시오.</sub>
- <sub>**Hidden API 정책.** 앞으로 `HiddenApiBypass`가 막히면 `cmd window density` 경로로 넘어가며, 둘 다 막히면 실패 로그를 남깁니다.</sub>
- <sub>**주사율 고정과 적응형 주사율.** One UI 6·7 일부 기기는 최고·최저 주사율을 같게 둬도 화면을 한동안 만지지 않으면 디스플레이가 스스로 주사율을 낮춘다는 보고가 있습니다. GOAT는 이 동작을 막지 않습니다.</sub>
- <sub>**Direct Boot.** 잠금 해제 전에는 WorkManager를 쓸 수 없어 1회만 확인합니다.</sub>
- <sub>**서명.** release APK도 debug 키로 서명하므로 Play Protect 경고가 나올 수 있습니다.</sub>

### 라이선스

<sub>Copyright (C) 2026 lala5bok</sub>

<sub>이 프로그램은 [GNU General Public License v3.0](LICENSE)(GPL-3.0)으로 배포하는 자유 소프트웨어입니다. 누구나 무료로 쓰고, 고치고, 다시 배포할 수 있습니다. 다만 고친 버전이나 이 코드를 포함한 프로그램을 배포할 때는 그 소스도 GPL-3.0으로 공개해야 합니다.</sub>

<sub>GPL-3.0 조건(소스 공개)을 따르기 어려운 회사나 제품에는 별도 상용 라이선스를 제공합니다. 문의: lala5bok@gmail.com</sub>

<sub>이 앱이 쓰는 라이브러리 중 AndroidX·Jetpack Compose·HiddenApiBypass·Haze는 Apache License 2.0, Shizuku-API는 MIT License이며 모두 GPL-3.0과 함께 쓸 수 있습니다.</sub>

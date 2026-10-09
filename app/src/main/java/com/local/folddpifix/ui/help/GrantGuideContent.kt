package com.local.folddpifix.ui.help

/**
 * PC adb로 WRITE_SECURE_SETTINGS를 부여하는 안내 문구.
 * 연결 방식(USB/무선)과 PC 운영체제에 따라 단계와 명령이 달라진다. Android 의존성이 없어 단위 테스트로 검증한다.
 */
object GrantGuideContent {

    enum class Connection(val label: String) { USB("USB 케이블"), WIRELESS("무선") }
    enum class PcOs(val label: String) { WINDOWS("Windows"), MAC("Mac"), LINUX("Linux") }

    data class Step(
        val title: String,
        val body: String,
        val commands: List<String> = emptyList(),
        /** 이 단계에 [개발자 옵션 열기] 버튼을 보여 줄지 */
        val openDevOptions: Boolean = false,
        /** 복사·열기용 링크(이름 → 주소) */
        val links: List<Pair<String, String>> = emptyList(),
    ) {
        /** 이 단계에 보여 줄 그림 장면. */
        val scene: com.local.folddpifix.ui.art.GuideScene
            get() = when {
                title.startsWith("폰 준비") -> com.local.folddpifix.ui.art.GuideScene.DEV_OPTIONS
                title.startsWith("PC에 adb 설치") -> com.local.folddpifix.ui.art.GuideScene.INSTALL
                title.startsWith("페어링") -> com.local.folddpifix.ui.art.GuideScene.PAIR
                title.startsWith("연결 확인") -> com.local.folddpifix.ui.art.GuideScene.USB_CONNECT
                title.startsWith("연결") -> com.local.folddpifix.ui.art.GuideScene.WIRELESS_CONNECT
                title.startsWith("권한 부여") -> com.local.folddpifix.ui.art.GuideScene.COMMAND
                title.startsWith("마무리") -> com.local.folddpifix.ui.art.GuideScene.DONE
                else -> com.local.folddpifix.ui.art.GuideScene.USB_CONNECT
            }
    }

    const val URL_PLATFORM_TOOLS = "https://developer.android.com/tools/releases/platform-tools"
    const val URL_SAMSUNG_DRIVER = "https://developer.samsung.com/android-usb-driver"
    const val URL_HOMEBREW = "https://brew.sh"

    /** PC로 보내기(메일·메신저)용 전체 안내문. */
    fun fullText(connection: Connection, os: PcOs, appName: String): String = buildString {
        appendLine("$appName 권한 설정 안내 (${os.label} · ${connection.label})")
        appendLine()
        appendLine("휴대폰에서 $appName 앱이 디스플레이 밀도를 바꾸려면 WRITE_SECURE_SETTINGS 권한이 필요합니다.")
        appendLine("PC의 adb로 처음 한 번만 부여하면 되고, 재부팅해도 유지됩니다.")
        steps(connection, os).forEachIndexed { i, step ->
            appendLine()
            appendLine("${i + 1}. ${step.title}")
            appendLine(step.body)
            step.links.forEach { (name, url) -> appendLine("   - $name: $url") }
            step.commands.forEach { appendLine("   $ $it") }
        }
        appendLine()
        appendLine("■ 안 될 때")
        troubles(connection, os).forEach { tr ->
            appendLine()
            appendLine("- ${tr.title}" + (tr.error?.let { " ($it)" } ?: ""))
            appendLine(tr.body)
            tr.commands.forEach { appendLine("   $ $it") }
        }
    }

    /** 자주 나오는 오류와 해결. 오류 문구(error)는 PC 화면에 찍히는 그대로 적어 찾기 쉽게 한다. */
    data class Trouble(val title: String, val error: String?, val body: String, val commands: List<String> = emptyList())

    fun troubles(connection: Connection, os: PcOs): List<Trouble> {
        val adb = adb(os)
        return buildList {
            if (connection == Connection.WIRELESS) {
                add(Trouble(
                    "무선 연결이 안 될 때", "protocol fault (couldn't read status message)",
                    "① 폰의 VPN을 끕니다(켜져 있으면 같은 와이파이에서도 서로 찾지 못합니다).\n" +
                        "② 폰과 PC가 같은 와이파이인지 확인합니다.\n" +
                        "③ adb를 다시 시작하고, 그래도 안 되면 폰과 PC를 재시작합니다.\n" +
                        "④ Windows 방화벽이 adb 허용을 물으면 허용합니다.",
                    listOf("$adb kill-server", "$adb start-server"),
                ))
                add(Trouble(
                    "포트 번호가 헷갈릴 때", null,
                    "adb pair에는 '페어링 코드로 기기 페어링' 팝업의 IP:포트를, adb connect에는 무선 디버깅 첫 화면의 'IP 주소 및 포트'를 씁니다. 두 포트는 서로 다르고, 무선 디버깅을 껐다 켜면 바뀝니다.",
                ))
            }
            add(Trouble(
                "기기가 두 개 이상이라고 나올 때", "more than one device/emulator",
                "같은 폰이 두 번 잡힌 경우입니다(무선은 페어링 연결과 connect 연결, 또는 USB와 무선). 목록에서 상태가 device인 것 하나를 골라 -s 뒤에 그 이름을 넣어 실행합니다.",
                listOf("$adb devices", "$adb -s 목록의이름 shell pm grant $PACKAGE $PERMISSION"),
            ))
            if (connection == Connection.USB) {
                add(Trouble(
                    "unauthorized로 나올 때", "unauthorized",
                    "폰에 뜬 'USB 디버깅을 허용하시겠습니까?'에서 [허용]을 누릅니다. 창이 안 보이면 개발자 옵션 → 'USB 디버깅 권한 승인 취소' 후 케이블을 다시 꽂습니다.",
                ))
                add(Trouble(
                    "기기가 안 보일 때", "no devices/emulators found",
                    "충전 전용이 아닌 데이터 케이블인지, USB 디버깅이 켜져 있는지 확인합니다." +
                        (if (os == PcOs.WINDOWS) " Windows는 Samsung USB 드라이버가 필요할 수 있습니다." else ""),
                    listOf("$adb devices"),
                ))
            }
            add(Trouble(
                "adb를 찾을 수 없다고 나올 때", if (os == PcOs.WINDOWS) "'adb' is not recognized" else "command not found: adb",
                if (os == PcOs.WINDOWS) "platform-tools 폴더에서 연 PowerShell에서 adb 앞에 .\\를 붙여 실행합니다."
                else "설치하지 않았다면 위 단계로 설치하고, 압축본을 쓰면 그 폴더에서 ./adb로 실행합니다.",
            ))
            add(Trouble(
                "오래된 adb일 때", null,
                "adb version이 30 이상이어야 무선 페어링이 됩니다. 다른 프로그램(Smart Switch 등)에 딸린 옛 adb가 먼저 실행되는지도 확인합니다.",
                listOf("$adb version") + (if (os == PcOs.WINDOWS) listOf("where adb") else listOf("which adb")),
            ))
        }
    }

    const val PACKAGE = "com.local.folddpifix"
    const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    /** 운영체제와 무관한 기본 형태(권한 카드에 표시). */
    const val GRANT_COMMAND = "adb shell pm grant $PACKAGE $PERMISSION"

    /** 운영체제별 adb 실행 방식. Windows는 platform-tools 폴더에서 .\adb 로 실행한다. */
    fun adb(os: PcOs): String = if (os == PcOs.WINDOWS) ".\\adb" else "adb"

    fun grantCommand(os: PcOs) = "${adb(os)} shell pm grant $PACKAGE $PERMISSION"

    fun steps(connection: Connection, os: PcOs): List<Step> {
        val adb = adb(os)
        val list = mutableListOf<Step>()

        list += when (connection) {
            Connection.USB -> Step(
                "폰 준비",
                "① 설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호를 7번 눌러 개발자 옵션을 켭니다.\n" +
                    "② 개발자 옵션 → USB 디버깅을 켭니다.\n" +
                    "③ USB 케이블로 PC에 연결합니다.",
                openDevOptions = true,
            )
            Connection.WIRELESS -> Step(
                "폰 준비",
                "① 설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호를 7번 눌러 개발자 옵션을 켭니다.\n" +
                    "② 폰과 PC를 같은 Wi-Fi에 연결합니다.\n" +
                    "③ 개발자 옵션 → 무선 디버깅을 켭니다.",
                openDevOptions = true,
            )
        }

        list += installStep(connection, os)

        when (connection) {
            Connection.USB -> {
                list += Step(
                    "연결 확인",
                    "아래 명령을 실행합니다. 폰에 'USB 디버깅을 허용하시겠습니까?'가 뜨면 [허용]을 누릅니다. " +
                        "목록에 기기가 device 로 보이면 준비가 끝난 것입니다.",
                    listOf("$adb devices"),
                )
            }
            Connection.WIRELESS -> {
                list += Step(
                    "페어링 (처음 한 번)",
                    "폰: 무선 디버깅 화면에서 '페어링 코드로 기기 페어링'을 누릅니다. 창에 나온 IP 주소:포트와 " +
                        "6자리 코드를 확인합니다. 이 창은 페어링이 끝날 때까지 열어 둡니다.\n" +
                        "PC: 아래 명령의 IP:포트를 바꿔 실행하고, 코드를 물으면 6자리를 입력합니다. " +
                        "'Successfully paired'가 나오면 성공입니다.",
                    listOf("$adb pair 192.168.0.10:37123"),
                )
                list += Step(
                    "연결",
                    "무선 디버깅 첫 화면의 'IP 주소 및 포트'(페어링 포트와 다릅니다)로 연결합니다. " +
                        "'connected to …'가 나오면 성공입니다.",
                    listOf("$adb connect 192.168.0.10:41234", "$adb devices"),
                )
            }
        }

        list += Step(
            "권한 부여",
            "아래 명령을 실행합니다. 아무 출력 없이 끝나면 성공입니다.",
            listOf(grantCommand(os)),
        )
        list += Step(
            "마무리",
            "이 앱으로 돌아오면 권한을 자동으로 확인합니다. 권한은 재부팅해도 유지되니, 확인되면 " +
                (if (connection == Connection.USB) "USB 디버깅" else "무선 디버깅") + "은 꺼도 됩니다.",
        )
        return list
    }

    private fun installStep(connection: Connection, os: PcOs): Step {
        val pairNote = if (connection == Connection.WIRELESS) {
            "\n무선 페어링(adb pair)은 adb 30 이상에서만 됩니다. adb version 으로 확인하십시오."
        } else {
            ""
        }
        return when (os) {
            PcOs.WINDOWS -> Step(
                "PC에 adb 설치 (Windows)",
                links = listOfNotNull(
                    "SDK Platform-Tools" to URL_PLATFORM_TOOLS,
                    if (connection == Connection.USB) "Samsung USB 드라이버" to URL_SAMSUNG_DRIVER else null,
                ),
                body =
                "① developer.android.com/tools/releases/platform-tools 에서 'SDK Platform-Tools for Windows'를 받아 압축을 풉니다.\n" +
                    "② platform-tools 폴더의 빈 곳을 Shift+우클릭 → 'PowerShell 창 열기' 또는 '터미널에서 열기'를 누릅니다.\n" +
                    "③ 아래 단계의 명령은 이 창에서 .\\adb 로 실행합니다." +
                    (if (connection == Connection.USB) "\n폰이 인식되지 않으면 Samsung 'Android USB Driver for Windows'를 설치합니다." else "") +
                    pairNote,
            )
            PcOs.MAC -> Step(
                "PC에 adb 설치 (Mac)",
                links = listOf("Homebrew" to URL_HOMEBREW, "SDK Platform-Tools" to URL_PLATFORM_TOOLS),
                body =
                "Homebrew가 있으면 아래 명령으로 설치합니다. 없으면 위 사이트에서 'SDK Platform-Tools for Mac'을 받아 " +
                    "압축을 풀고, 그 폴더에서 adb 대신 ./adb 로 실행합니다." + pairNote,
                commands = listOf("brew install --cask android-platform-tools"),
            )
            PcOs.LINUX -> Step(
                "PC에 adb 설치 (Linux)",
                links = listOf("SDK Platform-Tools" to URL_PLATFORM_TOOLS),
                body =
                "배포판 패키지로 설치합니다. Fedora는 sudo dnf install android-tools, Arch는 sudo pacman -S android-tools 입니다." +
                    (if (connection == Connection.USB) {
                        "\nadb devices에 'no permissions'가 뜨면 Ubuntu·Debian에서는 " +
                            "sudo apt install android-sdk-platform-tools-common 을 설치한 뒤 케이블을 다시 연결합니다."
                    } else {
                        "\n배포판 adb가 30보다 낮으면 developer.android.com 의 'SDK Platform-Tools for Linux'를 받아 ./adb 로 실행합니다."
                    }) + pairNote,
                commands = listOf("sudo apt install adb"),
            )
        }
    }
}

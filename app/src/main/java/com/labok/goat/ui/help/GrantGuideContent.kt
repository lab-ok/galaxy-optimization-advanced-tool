package com.labok.goat.ui.help

import com.labok.goat.R
import com.labok.goat.ui.art.GuideScene
import com.labok.goat.ui.text.Copy

/**
 * PC adb로 WRITE_SECURE_SETTINGS를 부여하는 안내 문구.
 * 연결 방식(USB/무선)과 PC 운영체제에 따라 단계와 명령이 달라진다. Android 의존성이 없어 단위 테스트로 검증한다.
 */
object GrantGuideContent {

    enum class Connection(private val labelRes: Int) {
        USB(R.string.help_grant_conn_usb),
        WIRELESS(R.string.help_grant_conn_wireless);

        val label: String get() = Copy.text(labelRes)
    }
    enum class PcOs(val label: String) { WINDOWS("Windows"), MAC("Mac"), LINUX("Linux") }

    data class Step(
        val title: String,
        val body: String,
        val commands: List<String> = emptyList(),
        /** 이 단계에 [개발자 옵션 열기] 버튼을 보여 줄지 */
        val openDevOptions: Boolean = false,
        /** 복사·열기용 링크(이름 → 주소) */
        val links: List<Pair<String, String>> = emptyList()
    ) {
        /** 이 단계에 보여 줄 그림 장면. */
        val scene: GuideScene
            get() = when {
                title.startsWith(Copy.text(R.string.help_grant_step_prepare)) -> GuideScene.DEV_OPTIONS
                title.startsWith(Copy.text(R.string.help_grant_step_install)) -> GuideScene.INSTALL
                title.startsWith(Copy.text(R.string.help_grant_step_pair)) -> GuideScene.PAIR
                title.startsWith(Copy.text(R.string.help_grant_step_check)) -> GuideScene.USB_CONNECT
                title.startsWith(Copy.text(R.string.help_grant_step_connect)) -> GuideScene.WIRELESS_CONNECT
                title.startsWith(Copy.text(R.string.help_grant_step_grant)) -> GuideScene.COMMAND
                title.startsWith(Copy.text(R.string.help_grant_step_done)) -> GuideScene.DONE
                else -> GuideScene.USB_CONNECT
            }
    }

    const val URL_PLATFORM_TOOLS = "https://developer.android.com/tools/releases/platform-tools"
    const val URL_SAMSUNG_DRIVER = "https://developer.samsung.com/android-usb-driver"
    const val URL_HOMEBREW = "https://brew.sh"

    /** PC로 보내기(메일·메신저)용 전체 안내문. */
    fun fullText(connection: Connection, os: PcOs, appName: String): String = buildString {
        appendLine(Copy.text(R.string.help_grant_full_title_fmt, appName, os.label, connection.label))
        appendLine()
        appendLine(Copy.text(R.string.help_grant_full_intro_fmt, appName))
        appendLine(Copy.text(R.string.help_grant_full_persist))
        steps(connection, os).forEachIndexed { i, step ->
            appendLine()
            appendLine("${i + 1}. ${step.title}")
            appendLine(step.body)
            step.links.forEach { (name, url) -> appendLine("   - $name: $url") }
            step.commands.forEach { appendLine("   $ $it") }
        }
        appendLine()
        appendLine(Copy.text(R.string.help_grant_full_troubles))
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
                add(
                    Trouble(
                        Copy.text(R.string.help_trouble_wireless_title),
                        "protocol fault (couldn't read status message)",
                        Copy.text(R.string.help_trouble_wireless_body),
                        listOf("$adb kill-server", "$adb start-server")
                    )
                )
                add(
                    Trouble(
                        Copy.text(R.string.help_trouble_ports_title),
                        null,
                        Copy.text(R.string.help_trouble_ports_body)
                    )
                )
            }
            add(
                Trouble(
                    Copy.text(R.string.help_trouble_multiple_title),
                    "more than one device/emulator",
                    Copy.text(R.string.help_trouble_multiple_body),
                    listOf("$adb devices", "$adb -s ${Copy.text(R.string.help_trouble_multiple_placeholder)} shell pm grant $PACKAGE $PERMISSION")
                )
            )
            if (connection == Connection.USB) {
                add(
                    Trouble(
                        Copy.text(R.string.help_trouble_unauthorized_title),
                        "unauthorized",
                        Copy.text(R.string.help_trouble_unauthorized_body)
                    )
                )
                add(
                    Trouble(
                        Copy.text(R.string.help_trouble_no_device_title),
                        "no devices/emulators found",
                        Copy.text(R.string.help_trouble_no_device_body) +
                            (if (os == PcOs.WINDOWS) Copy.text(R.string.help_trouble_no_device_windows) else ""),
                        listOf("$adb devices")
                    )
                )
            }
            add(
                Trouble(
                    Copy.text(R.string.help_trouble_no_adb_title),
                    if (os == PcOs.WINDOWS) "'adb' is not recognized" else "command not found: adb",
                    if (os == PcOs.WINDOWS) {
                        Copy.text(R.string.help_trouble_no_adb_windows)
                    } else {
                        Copy.text(R.string.help_trouble_no_adb_other)
                    }
                )
            )
            add(
                Trouble(
                    Copy.text(R.string.help_trouble_old_adb_title),
                    null,
                    Copy.text(R.string.help_trouble_old_adb_body),
                    listOf("$adb version") + (if (os == PcOs.WINDOWS) listOf("where adb") else listOf("which adb"))
                )
            )
        }
    }

    const val PACKAGE = "com.labok.goat"
    const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    /** 운영체제별 adb 실행 방식. Windows는 platform-tools 폴더에서 .\adb 로 실행한다. */
    fun adb(os: PcOs): String = if (os == PcOs.WINDOWS) ".\\adb" else "adb"

    fun grantCommand(os: PcOs) = "${adb(os)} shell pm grant $PACKAGE $PERMISSION"

    fun steps(connection: Connection, os: PcOs): List<Step> {
        val adb = adb(os)
        val list = mutableListOf<Step>()

        list += when (connection) {
            Connection.USB -> Step(
                Copy.text(R.string.help_grant_step_prepare),
                Copy.text(R.string.help_step_prepare_usb_body),
                openDevOptions = true
            )
            Connection.WIRELESS -> Step(
                Copy.text(R.string.help_grant_step_prepare),
                Copy.text(R.string.help_step_prepare_wireless_body),
                openDevOptions = true
            )
        }

        list += installStep(connection, os)

        when (connection) {
            Connection.USB -> {
                list += Step(
                    Copy.text(R.string.help_grant_step_check),
                    Copy.text(R.string.help_step_usb_check_body),
                    listOf("$adb devices")
                )
            }
            Connection.WIRELESS -> {
                list += Step(
                    Copy.text(R.string.help_grant_step_pair_first),
                    Copy.text(R.string.help_step_pair_body),
                    listOf("$adb pair 192.168.0.10:37123")
                )
                list += Step(
                    Copy.text(R.string.help_grant_step_connect),
                    Copy.text(R.string.help_step_connect_body),
                    listOf("$adb connect 192.168.0.10:41234", "$adb devices")
                )
            }
        }

        list += Step(
            Copy.text(R.string.help_grant_step_grant),
            Copy.text(R.string.help_step_grant_body),
            listOf(grantCommand(os))
        )
        list += Step(
            Copy.text(R.string.help_grant_step_done),
            Copy.text(R.string.help_step_done_body_fmt, if (connection == Connection.USB) Copy.text(R.string.help_step_done_usb) else Copy.text(R.string.help_step_done_wireless))
        )
        return list
    }

    private fun installStep(connection: Connection, os: PcOs): Step {
        val pairNote = if (connection == Connection.WIRELESS) {
            Copy.text(R.string.help_install_pair_note)
        } else {
            ""
        }
        return when (os) {
            PcOs.WINDOWS -> Step(
                Copy.text(R.string.help_grant_step_install_os_fmt, "Windows"),
                links = listOfNotNull(
                    "SDK Platform-Tools" to URL_PLATFORM_TOOLS,
                    if (connection == Connection.USB) Copy.text(R.string.help_install_link_samsung_driver) to URL_SAMSUNG_DRIVER else null
                ),
                body =
                Copy.text(R.string.help_install_windows_body) +
                    (if (connection == Connection.USB) Copy.text(R.string.help_install_windows_driver) else "") +
                    pairNote
            )
            PcOs.MAC -> Step(
                Copy.text(R.string.help_grant_step_install_os_fmt, "Mac"),
                links = listOf("Homebrew" to URL_HOMEBREW, "SDK Platform-Tools" to URL_PLATFORM_TOOLS),
                body =
                Copy.text(R.string.help_install_mac_body) + pairNote,
                commands = listOf("brew install --cask android-platform-tools")
            )
            PcOs.LINUX -> Step(
                Copy.text(R.string.help_grant_step_install_os_fmt, "Linux"),
                links = listOf("SDK Platform-Tools" to URL_PLATFORM_TOOLS),
                body =
                Copy.text(R.string.help_install_linux_body) +
                    (
                        if (connection == Connection.USB) {
                            Copy.text(R.string.help_install_linux_usb)
                        } else {
                            Copy.text(R.string.help_install_linux_wireless)
                        }
                        ) + pairNote,
                commands = listOf("sudo apt install adb")
            )
        }
    }
}

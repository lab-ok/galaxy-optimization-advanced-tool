package com.local.folddpifix.ui.text

/**
 * 화면 문구 한곳. 원칙: 사용자가 알아야 할 것만 한 문장으로. 지나치게 풀어 쓰지 않고 DPI·해상도·권한처럼
 * 표준 용어를 그대로 쓴다. 어조는 시스템 설정 앱처럼 "~합니다/~하세요".
 */
object Copy {
    // 상태
    const val OUTER = "외부 화면"
    const val INNER = "내부 화면"
    const val CURRENT = "현재 화면"
    const val STATUS_OK = "두 화면 표시 크기 일치"
    const val STATUS_FIXING = "DPI 적용 중"
    const val STATUS_NEED_SETUP = "권한 설정 필요"
    const val STATUS_NO_TARGET = "외부 화면 DPI 설정 필요"
    const val STATUS_HOLD = "자동 적용 보류"
    const val STATUS_NEED_OTHER = "외부 화면만 적용됨"

    // 할 일
    const val TODO_SETUP_TITLE = "권한 설정 필요"
    const val TODO_SETUP_BODY = "DPI 변경에는 WRITE_SECURE_SETTINGS 권한이 필요합니다. PC의 adb로 최초 1회만 부여합니다."
    const val TODO_SETUP_ACTION = "설정 방법"
    const val TODO_OTHER_TITLE = "화면 정보 수집 필요"
    const val TODO_OTHER_BODY = "기기를 한 번 접었다 펼치면 두 화면의 해상도를 읽어 내부 화면 DPI를 계산합니다."
    const val TODO_HOLD_TITLE = "DPI 계산 보류"
    const val TODO_HOLD_BODY = "화면 정보가 올바르지 않아 적용을 멈췄습니다. 화면 정보를 다시 수집하세요."
    const val TODO_HOLD_ACTION = "다시 수집"
    const val TODO_APPLY_TITLE = "설정값이 적용되지 않았습니다"
    const val TODO_APPLY_ACTION = "지금 적용"
    const val TODO_AUTO_TITLE = "자동 적용 꺼짐"
    const val TODO_AUTO_BODY = "재부팅하면 기기 기본 DPI로 돌아갑니다."
    const val TODO_AUTO_ACTION = "켜기"
    const val TODO_CRASH_TITLE = "비정상 종료 기록이 있습니다"
    const val TODO_CRASH_BODY = "문제 신고로 진단 파일을 보내 주세요."
    const val TODO_CRASH_ACTION = "문제 신고"

    // 외부 DPI 변경
    const val EXTERNAL_TITLE = "외부에서 DPI가 변경됨"
    const val EXTERNAL_BODY = "이 값을 기준으로 할지, 앱 설정으로 복원할지 선택하세요."
    const val STATUS_EXTERNAL = "외부에서 변경된 DPI"

    // 크기 설정
    const val SIZE_TEST = "크기 테스트"
    const val SIZE_TITLE = "표시 크기"
    const val SIZE_OUTER = "외부 화면 DPI"
    const val SIZE_OUTER_HINT = "값이 작을수록 글자와 아이콘이 작게 표시됩니다"
    const val SIZE_INNER_TUNE = "내부 화면 보정"
    const val SIZE_INNER_TUNE_HINT = "내부 화면이 크게 보이면 −, 작게 보이면 + 방향으로 조정합니다"
    const val SIZE_PREVIEW_OUTER = "외부"
    const val SIZE_PREVIEW_INNER = "내부"

    // 자동
    const val AUTO_TITLE = "자동 적용"
    const val AUTO_BODY = "재부팅하거나 화면이 전환되면 설정한 DPI를 다시 적용합니다"

    // 메뉴
    const val FEATURE_DPI = "화면 크기 맞추기"
    const val FEATURE_APP_SIZE = "앱별 화면 크기"
    const val MENU_HELP = "사용 방법"
    const val MENU_GUIDE = "권한 설정"
    const val MENU_SECTION_FEATURES = "기능"
    const val MENU_SECTION_LAB = "실험실"
    const val MENU_ADVANCED = "고급 정보"
    const val MENU_REPORT = "문제 신고"
    const val MENU_TEST = "크기 테스트"
    const val MENU_RESET = "초기화"
    const val MENU_ABOUT = "앱 정보"

    // 초기화
    const val RESET_TITLE = "초기화"
    const val RESET_RELEARN = "화면 정보 초기화"
    const val RESET_RELEARN_BODY = "학습한 해상도·PPI 정보를 지우고 다시 수집합니다. 설정한 DPI는 유지됩니다."
    const val RESET_DEFAULT = "기본 DPI로 복원"
    const val RESET_PERMISSION = "권한 초기화"
    const val RESET_PERMISSION_BODY = "adb로 부여한 WRITE_SECURE_SETTINGS 권한과 앱의 적용 기록을 초기화합니다."
    const val RESET_PERMISSION_WARN = "자동 적용이 중지되고 적용 기록이 삭제됩니다. 다시 쓰려면 PC에서 권한을 다시 부여해야 합니다."
    const val RESET_PERMISSION_CONFIRM = "정말 권한을 초기화하시겠습니까? 이 작업은 되돌릴 수 없습니다."
    const val RESET_PERMISSION_KEEP = "현재 적용된 DPI는 그대로 유지됩니다."
    const val RESET_PERMISSION_LIMIT = "Android는 앱이 이 권한을 스스로 회수하지 못하게 막아 두어, 마지막 회수는 PC 명령 한 줄로 합니다."
    const val RESET_PERMISSION_STEP = "PC에서 아래 명령을 실행하면 권한이 제거되고, 앱이 자동으로 권한 설정 화면을 띄웁니다."
    const val RESET_PERMISSION_WAITING = "권한 회수 대기 중"
    const val CONTINUE = "계속"
    const val CANCEL = "취소"
    const val RESET_DEFAULT_BODY = "두 화면을 기기 기본 DPI로 되돌리고 자동 적용을 끕니다."

    // 알림(토스트)
    const val TOAST_APPLIED = "적용했습니다"
    const val TOAST_FAILED = "적용하지 못했습니다. 문제 신고로 알려 주세요"
    const val TOAST_NO_PERMISSION = "먼저 권한을 설정하세요"
    const val TOAST_BUSY = "잠시 후 다시 시도하세요"
    const val TOAST_RELEARN = "화면 정보를 초기화했습니다. 기기를 접었다 펼치세요"
    const val TOAST_RESET = "기본 DPI로 복원했습니다"
    const val TOAST_PERMISSION_OK = "권한 설정이 완료되었습니다"
    const val TOAST_ADOPTED = "변경된 값을 기준으로 설정했습니다"
    const val TOAST_RESTORED = "앱 설정으로 복원했습니다"
    const val TOAST_PERMISSION_RESET = "앱 설정을 초기화했습니다. PC에서 권한을 회수하세요"
    const val TOAST_PERMISSION_REMOVED = "권한이 제거되었습니다"
    const val TOAST_COPIED = "복사했습니다"
    const val TOAST_REPORT_SAVED = "다운로드/FoldFit 폴더에 저장했습니다"
    const val TOAST_REPORT_FAILED = "진단 파일을 만들지 못했습니다"
}

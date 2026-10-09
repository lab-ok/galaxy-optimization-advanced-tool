package com.local.folddpifix.data.display

import android.Manifest
import android.content.Intent
import android.net.Uri

/**
 * adb로 부여한 WRITE_SECURE_SETTINGS 권한을 없애는 방법(권한 부여 과정을 다시 시험할 때).
 * 이 권한은 권한 그룹에 속하지 않아 앱이 스스로 회수할 수 없다(revokeSelfPermissionsOnKill은
 * "does not belong to a permission group"으로 거부됨, redroid Android 14에서 확인). 그래서 두 가지만 쓴다.
 * - 앱 삭제: 삭제하면 권한도 함께 사라진다. 다시 설치하면 권한이 없는 처음 상태가 된다.
 * - PC에서 [command] 실행: 앱과 설정을 그대로 둔 채 권한만 회수한다.
 */
object PermissionReset {
    const val PERMISSION = Manifest.permission.WRITE_SECURE_SETTINGS

    fun command(packageName: String) = "adb shell pm revoke $packageName $PERMISSION"

    /** 시스템 앱 삭제 확인 창. */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

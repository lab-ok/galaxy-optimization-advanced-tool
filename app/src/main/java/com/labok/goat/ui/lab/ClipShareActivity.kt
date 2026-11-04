package com.labok.goat.ui.lab

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.labok.goat.data.lab.MemoShare

/**
 * 클립보드의 글을 곧바로 TXT 파일로 공유하는 투명 화면. 공유 메뉴가 없는 앱에서 글을 복사만 한 경우에 쓴다.
 * 빠른 설정 타일([TxtShareTileService])이나 GOAT 아이콘을 길게 누른 바로가기에서 연다.
 * 안드로이드는 앞에 떠서 포커스를 받은 앱만 클립보드를 읽게 하므로, 창이 포커스를 받은 뒤에 읽는다.
 * 공유를 마치고 돌아오면 파일을 지우고 닫는다.
 */
class ClipShareActivity : Activity() {
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        started = savedInstanceState?.getBoolean("started") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("started", started)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || started) return
        started = true
        // 클립보드는 포커스를 가진 지금만 읽을 수 있어 바로 읽고, 파일 복사는 작업 스레드에서 한다
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
        Thread {
            val files = clip?.let { MemoShare.saveClip(this, it) }.orEmpty()
            runOnUiThread {
                if (files.isEmpty()) {
                    Toast.makeText(this, "클립보드가 비어 있습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    @Suppress("DEPRECATION")
                    startActivityForResult(MemoShare.chooser(this, files), REQUEST)
                }
            }
        }.start()
    }

    @Deprecated("Activity 결과 API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST) {
            MemoShare.clear(this)
            finish()
        }
    }

    private companion object {
        const val REQUEST = 1
    }
}

package com.labok.goat.ui.lab

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.labok.goat.data.lab.MemoShare

/**
 * 다른 앱의 공유 메뉴에 나타나는 'txt 파일로 공유': 받은 글을 임시 txt 파일로 바꿔 곧바로 공유 창을 다시 연다.
 * 공유를 마치고 돌아오면 파일을 지우고 닫는다. 화면은 그리지 않는다(투명).
 */
class MemoShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (text.isNullOrBlank()) {
            finish()
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(MemoShare.chooser(this, MemoShare.save(this, text)), REQUEST)
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

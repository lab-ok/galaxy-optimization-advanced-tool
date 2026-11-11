package com.labok.goat.ui.text

import com.labok.goat.core.R

/** 한국어 조사: 앞 낱말 끝소리에 맞춘 '(으)로'. 숫자로 끝나면 읽는 소리(0·3·6은 받침 ㅇ·ㅁ·ㄱ이 아닌 '으로')를 따른다. */
object Particle {
    fun ro(word: String): String = when (word.lastOrNull { it.isLetterOrDigit() }) {
        '0', '3', '6' -> Copy.text(R.string.update_particle_euro)
        else -> Copy.text(R.string.update_particle_ro)
    }
}

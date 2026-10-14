package com.labok.goat.ui.liquid

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * 앱 공통 글자(Material3 Text와 같은 모양): 한국어를 어절(띄어쓰기) 단위로만 줄바꿈한다(CSS word-break: keep-all).
 * 안드로이드 기본 줄바꿈은 한글 글자 사이 어디서나 끊어, 좁은 화면에서 '씁니/다.'처럼 낱글자만 다음 줄로 넘어갔다.
 * 구절 줄바꿈(LineBreak.WordBreak.Phrase)은 OS 버전·기기 언어에 따라 한국어에 적용되지 않아, 한글 글자 사이에
 * 폭 없는 이음표(U+2060 WORD JOINER)를 넣어 그 자리에서 끊지 못하게 한다. 띄어쓰기에서는 그대로 끊긴다.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val shown = remember(text) { keepAll(text) }
    androidx.compose.material3.Text(
        shown, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign,
        lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style
    )
}

/** 한글 글자 사이에 WORD JOINER를 넣어 어절 안에서 줄이 끊기지 않게 한다. */
internal fun keepAll(s: String): String {
    if (s.none { it.isHangul() }) return s
    val b = StringBuilder(s.length + s.length / 2)
    for (i in s.indices) {
        val ch = s[i]
        b.append(ch)
        val next = s.getOrNull(i + 1) ?: continue
        if (ch.isHangul() && next.isHangul()) b.append('⁠')
    }
    return b.toString()
}

private fun Char.isHangul() = this in '가'..'힣' || this in 'ᄀ'..'ᇿ' || this in '㄰'..'㆏'

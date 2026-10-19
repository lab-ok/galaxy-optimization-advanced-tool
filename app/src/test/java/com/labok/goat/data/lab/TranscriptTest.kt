package com.labok.goat.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptTest {
    @Test
    fun headerLinesWithTime() {
        val text = "회의 녹음 10월 19일\n\n화자 1 00:00\n안녕하세요.\n오늘 일정 말씀드릴게요.\n\n화자 2 (00:12)\n네, 좋습니다.\n\n화자 1 00:20\n내일 3:30에 만나요."
        val p = Transcript.parse(text)!!
        assertEquals("회의 녹음 10월 19일", p.preamble)
        assertEquals(listOf("화자 1", "화자 2"), p.speakers)
        assertEquals(3, p.turns.size)
        assertEquals("00:12", p.turns[1].time)
        // 시간으로 끝나는 말은 화자 줄이 아니다
        assertEquals("내일 3:30에 만나요.", p.turns[2].text)
        assertEquals(
            "회의 녹음 10월 19일\n\n나 (00:00)\n안녕하세요.\n오늘 일정 말씀드릴게요.\n\n상대 (00:12)\n네, 좋습니다.\n\n나 (00:20)\n내일 3:30에 만나요.\n",
            Transcript.convert(p, "화자 1")
        )
    }

    @Test
    fun timeFirstAndRenamedSpeakers() {
        val text = "[00:01] 라준혁\n시작할게요\n[00:04] 김민\n네\n[01:02:03] 라준혁\n끝\n[01:02:09] 화자 3\n수고하셨습니다"
        val p = Transcript.parse(text)!!
        // 김민은 한 번만 말했지만 다른 화자 줄과 모양이 같고 시간이 이어져 화자로 받는다
        assertEquals(listOf("라준혁", "김민", "화자 3"), p.speakers)
        assertEquals("01:02:03", p.turns[2].time)
        val out = Transcript.convert(p, "라준혁")
        assertEquals("나 (00:01)\n시작할게요\n\n상대 (00:04)\n네\n\n나 (01:02:03)\n끝\n\n상대 (01:02:09)\n수고하셨습니다\n", out)
    }

    @Test
    fun lineWithEarlierTimeIsNotAHeader() {
        // 확실한 화자 줄 시간(01:10)보다 앞선 '회의 00:30'은 말 속의 문장으로 둔다
        val text = "화자 1 01:00\n안녕\n화자 2 01:10\n회의 00:30\n화자 1 01:20\n잘 가"
        val p = Transcript.parse(text)!!
        assertEquals(listOf("화자 1", "화자 2"), p.speakers)
        assertEquals("회의 00:30", p.turns[1].text)
    }

    @Test
    fun inlineSpeakers() {
        val text = "화자 1: 안녕하세요\n화자 2: 반갑습니다\n참고: 이건 메모\n[00:10] 화자 1: 다시 시작"
        val p = Transcript.parse(text)!!
        assertEquals(listOf("화자 1", "화자 2"), p.speakers)
        assertEquals("00:10", p.turns[2].time)
        // 한 번만 나온 '참고:'는 화자가 아니라 앞 화자의 이어진 말이다(버려지지 않는다)
        assertEquals("반갑습니다\n참고: 이건 메모", p.turns[1].text)
        assertEquals(2, p.count("화자 1"))
        assertEquals("안녕하세요", p.preview("화자 1"))
    }

    @Test
    fun plainTextHasNoSpeakers() {
        assertNull(Transcript.parse("그냥 메모입니다.\n내일 3:30 회의\n참고: 자료 첨부"))
    }

    @Test
    fun decodesBomAndUtf16() {
        val s = "화자 1 00:00\n안녕"
        assertEquals(s, Transcript.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + s.toByteArray()))
        assertEquals(s, Transcript.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + s.toByteArray(Charsets.UTF_16LE)))
        assertEquals("Speaker 1 00:00\nhi", Transcript.decode("Speaker 1 00:00\nhi".toByteArray(Charsets.UTF_16LE)))
        assertEquals(s, Transcript.decode(s.toByteArray()))
    }

    @Test
    fun fileNames() {
        assertEquals("회의 녹음 001 (나·상대).txt", Transcript.fileName("회의 녹음 001.txt", "전사문"))
        assertEquals("a_b (나·상대).txt", Transcript.fileName("a/b", "전사문"))
        assertEquals("전사문-261019 (나·상대).txt", Transcript.fileName(null, "전사문-261019"))
    }
}

package com.example.cursortalkandroid.data.remote

import com.example.cursortalkandroid.data.model.SseEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    @Test
    fun parsesChunkedEventsAndCrLf() {
        val parser = SseParser()

        assertEquals(emptyList<SseEvent>(), parser.feed("event: meta\r\ndata: {\"session"))
        assertEquals(
            listOf(SseEvent.Meta("abc")),
            parser.feed("Id\":\"abc\"}\r\n\r\n"),
        )
    }

    @Test
    fun parsesAllCursorTalkEventTypes() {
        val parser = SseParser()
        val events = parser.feed(
            """
            event: meta
            data: {"sessionId":"session-1"}

            event: delta
            data: {"text":"hello\nworld"}

            event: done
            data: {"sessionId":"session-1"}

            event: error
            data: {"error":"失敗"}

            """.trimIndent() + "\n\n",
        )

        assertEquals(
            listOf(
                SseEvent.Meta("session-1"),
                SseEvent.Delta("hello\nworld"),
                SseEvent.Done("session-1"),
                SseEvent.Error("失敗"),
            ),
            events,
        )
    }

    @Test
    fun joinsMultipleDataLinesAndReportsMalformedJson() {
        val parser = SseParser()
        val events = parser.feed(
            "event: delta\ndata: {\"text\":\ndata: \"value\"}\n\n" +
                "event: done\ndata: not-json\n\n",
        )

        assertEquals(SseEvent.Delta("value"), events[0])
        assertEquals(SseEvent.Error("完了情報を解析できませんでした。"), events[1])
    }

    @Test
    fun finishParsesEventWithoutTrailingSeparator() {
        val parser = SseParser()
        parser.feed("event: delta\ndata: {\"text\":\"最後\"}")

        assertEquals(listOf(SseEvent.Delta("最後")), parser.finish())
    }
}

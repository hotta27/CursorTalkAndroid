package com.example.cursortalkandroid.data.remote

import com.example.cursortalkandroid.data.model.SseEvent

class SseParser {
    private val buffer = StringBuilder()
    private val separator = Regex("\\r?\\n\\r?\\n")

    fun feed(chunk: String): List<SseEvent> {
        buffer.append(chunk)
        val events = mutableListOf<SseEvent>()

        while (true) {
            val match = separator.find(buffer) ?: break
            val block = buffer.substring(0, match.range.first)
            buffer.delete(0, match.range.last + 1)
            parseBlock(block)?.let(events::add)
        }
        return events
    }

    fun finish(): List<SseEvent> {
        if (buffer.isEmpty()) return emptyList()
        val block = buffer.toString()
        buffer.clear()
        return listOfNotNull(parseBlock(block))
    }

    private fun parseBlock(block: String): SseEvent? {
        var eventName = "message"
        val dataLines = mutableListOf<String>()

        block.lineSequence().forEach { rawLine ->
            val line = rawLine.removeSuffix("\r")
            when {
                line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()
                line.startsWith("data:") -> dataLines += line.substringAfter("data:").removePrefix(" ")
            }
        }
        if (dataLines.isEmpty()) return null

        val data = dataLines.joinToString("\n")
        return when (eventName) {
            "meta" -> decodeStringField(data, "sessionId")?.let(SseEvent::Meta)
                ?: SseEvent.Error("セッション情報を解析できませんでした。")
            "delta" -> decodeStringField(data, "text")?.let(SseEvent::Delta)
                ?: SseEvent.Error("応答データを解析できませんでした。")
            "done" -> decodeStringField(data, "sessionId")?.let(SseEvent::Done)
                ?: SseEvent.Error("完了情報を解析できませんでした。")
            "error" -> SseEvent.Error(
                decodeStringField(data, "error") ?: "応答中にエラーが発生しました。",
            )
            else -> null
        }
    }

    private fun decodeStringField(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex < 0) return null
        var index = json.indexOf(':', keyIndex + key.length + 2)
        if (index < 0) return null
        index++
        while (index < json.length && json[index].isWhitespace()) index++
        if (index >= json.length || json[index] != '"') return null
        index++

        val result = StringBuilder()
        while (index < json.length) {
            when (val char = json[index++]) {
                '"' -> return result.toString()
                '\\' -> {
                    if (index >= json.length) return null
                    when (val escaped = json[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (index + 4 > json.length) return null
                            val codePoint = json.substring(index, index + 4).toIntOrNull(16)
                                ?: return null
                            result.append(codePoint.toChar())
                            index += 4
                        }
                        else -> return null
                    }
                }
                else -> result.append(char)
            }
        }
        return null
    }
}

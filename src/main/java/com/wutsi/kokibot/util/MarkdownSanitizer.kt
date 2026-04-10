package com.wutsi.kokibot.util

object MarkdownSanitizer {
    fun escape(text: String): String {
        // These characters MUST be escaped if they are intended to be plain text
        val reservedChars = listOf(
            "_", "*", "`", "|"
        )

        var sanitized = text
        reservedChars.forEach { char ->
            sanitized = sanitized.replace(char, "\\$char")
        }
        return sanitized
    }
}

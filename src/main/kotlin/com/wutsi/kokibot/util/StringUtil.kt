package com.wutsi.kokibot.util

object StringUtil {
    fun take(text: String, n: Int = 200): String {
        val xtext = text.replace("\n", " ").take(n).trim()
        return if (text.length > n) {
            "$xtext..."
        } else {
            xtext
        }
    }

    fun takeLast(text: String, n: Int = 200): String {
        val xtext = text.replace("\n", " ").take(n).trim().takeLast(n)
        return if (text.length > n) {
            "...$xtext"
        } else {
            xtext
        }
    }
}

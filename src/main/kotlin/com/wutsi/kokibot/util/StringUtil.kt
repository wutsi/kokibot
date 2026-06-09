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
}

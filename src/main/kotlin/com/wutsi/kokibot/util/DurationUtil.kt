package com.wutsi.kokibot.util

object DurationUtil {
    val ONE_HOUR = 60 * 60 * 1000L
    val ONE_MINUTE = 60 * 1000L
    val ONE_DAY = 24 * ONE_HOUR

    fun millis(earliest: String, default: Long = 0): Long {
        try {
            return when {
                earliest.endsWith("d") -> {
                    val days = earliest.dropLast(1).toIntOrNull() ?: 1
                    days * ONE_DAY
                }

                earliest.endsWith("h") -> {
                    val hours = earliest.dropLast(1).toIntOrNull() ?: 1
                    hours * ONE_HOUR
                }

                earliest.endsWith("m") -> {
                    val minutes = earliest.dropLast(1).toIntOrNull() ?: 1
                    minutes * ONE_MINUTE
                }

                else -> default
            }
        } catch (_: NumberFormatException) {
            return default
        }
    }
}

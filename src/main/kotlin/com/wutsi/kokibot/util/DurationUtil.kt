package com.wutsi.kokibot.util

object DurationUtil {
    val ONE_SECOND = 1000L
    val ONE_MINUTE = 60 * ONE_SECOND
    val ONE_HOUR = 60 * ONE_MINUTE
    val ONE_DAY = 24 * ONE_HOUR

    fun days(earliest: String, default: Long = 0): Long {
        return millis(earliest, default * ONE_DAY) / ONE_DAY
    }

    fun seconds(earliest: String, default: Long = 0): Long {
        return millis(earliest, default * ONE_SECOND) / ONE_SECOND
    }

    fun millis(earliest: String, default: Long = 0): Long {
        try {
            return when {
                earliest.endsWith("d", ignoreCase = true) -> {
                    val days = earliest.dropLast(1).toIntOrNull() ?: 1
                    days * ONE_DAY
                }

                earliest.endsWith("h", ignoreCase = true) -> {
                    val hours = earliest.dropLast(1).toIntOrNull() ?: 1
                    hours * ONE_HOUR
                }

                earliest.endsWith("m", ignoreCase = true) -> {
                    val minutes = earliest.dropLast(1).toIntOrNull() ?: 1
                    minutes * ONE_MINUTE
                }

                earliest.endsWith("s", ignoreCase = true) -> {
                    val seconds = earliest.dropLast(1).toIntOrNull() ?: 1
                    seconds * ONE_SECOND
                }

                else -> default
            }
        } catch (_: NumberFormatException) {
            return default
        }
    }
}

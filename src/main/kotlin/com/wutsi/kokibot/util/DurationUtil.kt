package com.wutsi.kokibot.util

object DurationUtil {
    val ONE_SECOND = 1000L
    val ONE_MINUTE = 60 * ONE_SECOND
    val ONE_HOUR = 60 * ONE_MINUTE
    val ONE_DAY = 24 * ONE_HOUR

    fun hms(duration: Long): String {
        // Format duration from <hour>h<minute>m<second>s
        // Ex: 2h30m15s, 4m20s, 45s
        val hours = duration / ONE_HOUR
        val minutes = (duration % ONE_HOUR) / ONE_MINUTE
        val seconds = (duration % ONE_MINUTE) / ONE_SECOND
        val sb = StringBuilder()
        if (hours > 0) {
            sb.append("${hours}h")
        }
        if (minutes > 0) {
            sb.append("${minutes}m")
        }
        if (seconds > 0) {
            sb.append("${seconds}s")
        }
        return sb.toString()
    }

    fun days(earliest: String, default: Long = 0): Long {
        return millis(earliest, default * ONE_DAY) / ONE_DAY
    }

    fun minutes(earliest: String, default: Long = 0): Long {
        return millis(earliest, default * ONE_SECOND) / ONE_MINUTE
    }

    fun seconds(earliest: String, default: Long = 0): Long {
        return millis(earliest, default * ONE_SECOND) / ONE_SECOND
    }

    fun millis(earliest: String, default: Long = 0): Long {
        try {
            return when {
                earliest.endsWith("d", ignoreCase = true) -> {
                    val days = earliest.dropLast(1).toLong()
                    days * ONE_DAY
                }

                earliest.endsWith("h", ignoreCase = true) -> {
                    val hours = earliest.dropLast(1).toLong()
                    hours * ONE_HOUR
                }

                earliest.endsWith("m", ignoreCase = true) -> {
                    val minutes = earliest.dropLast(1).toLong()
                    minutes * ONE_MINUTE
                }

                earliest.endsWith("s", ignoreCase = true) -> {
                    val seconds = earliest.dropLast(1).toLong()
                    seconds * ONE_SECOND
                }

                else -> default
            }
        } catch (_: NumberFormatException) {
            return default
        }
    }
}

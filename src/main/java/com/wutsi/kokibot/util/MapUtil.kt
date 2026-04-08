package com.wutsi.kokibot.util

object MapUtil {
    val envRegex = Regex("""\$\{([^}]+)}""")

    fun toString(key: String, map: Map<*, *>): String? {
        return map[key]?.toString()
    }

    fun toInt(key: String, map: Map<*, *>): Int? {
        val value = map[key]
        return if (value is Int) {
            value
        } else {
            value?.toString()?.toInt()
        }
    }

    fun toLong(key: String, map: Map<*, *>): Long? {
        val value = map[key]
        return if (value is Long) {
            value
        } else {
            value?.toString()?.toLong()
        }
    }

    fun toDouble(key: String, map: Map<*, *>): Double? {
        val value = map[key]
        return if (value is Double) {
            value
        } else {
            value?.toString()?.toDouble()
        }
    }

    fun toBoolean(key: String, map: Map<*, *>): Boolean? {
        val value = map[key]
        return if (value is Boolean) {
            value
        } else {
            value?.toString()?.toBoolean()
        }
    }

    fun toMap(key: String, map: Map<*, *>): Map<*, *>? {
        val value = map[key]
        return if (value is Map<*, *>) {
            value
        } else {
            null
        }
    }

    fun toList(key: String, map: Map<*, *>): List<*>? {
        val value = map[key]
        return if (value is List<*>) {
            value
        } else {
            null
        }
    }

    fun applyEnv(values: Map<*, *>): Map<*, *> {
        return values.map { entry ->
            if (entry.value is String) {
                entry.key to applyEnv(entry.value as String)
            } else if (entry.value is Map<*, *>) {
                entry.key to applyEnv(entry.value as Map<*, *>)
            } else if (entry.value is List<*>) {
                entry.key to applyEnv(entry.value as List<*>)
            } else {
                entry.key to entry.value
            }
        }.toMap()
    }

    private fun applyEnv(values: List<*>): List<*> {
        return values.map { item ->
            if (item is String) {
                applyEnv(item)
            } else if (item is Map<*, *>) {
                applyEnv(item)
            } else if (item is List<*>) {
                applyEnv(item)
            } else {
                item
            }
        }
    }

    private fun applyEnv(value: String): String {
        return envRegex.replace(value) { matchResult ->
            val varName = matchResult.groups[1]?.value
            System.getenv(varName) ?: matchResult.value
        }
    }
}

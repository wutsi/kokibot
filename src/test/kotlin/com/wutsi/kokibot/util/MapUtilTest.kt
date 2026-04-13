package com.wutsi.kokibot.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertEquals

class MapUtilTest {
    @Test
    fun `to String`() {
        assertEquals("123", MapUtil.toString("key", mapOf("key" to "123")))
        assertEquals("123", MapUtil.toString("key", mapOf("key" to 123)))
        assertEquals(null, MapUtil.toString("key", mapOf("key" to null)))
    }

    @Test
    fun `to Int`() {
        assertEquals(123, MapUtil.toInt("key", mapOf("key" to "123")))
        assertEquals(123, MapUtil.toInt("key", mapOf("key" to 123)))
        assertEquals(null, MapUtil.toInt("key", mapOf("key" to null)))
    }

    @Test
    fun `to Long`() {
        assertEquals(123L, MapUtil.toLong("key", mapOf("key" to "123")))
        assertEquals(123L, MapUtil.toLong("key", mapOf("key" to 123L)))
        assertEquals(null, MapUtil.toLong("key", mapOf("key" to null)))
    }

    @Test
    fun `to Double`() {
        assertEquals(123.1, MapUtil.toDouble("key", mapOf("key" to "123.1")))
        assertEquals(123.1, MapUtil.toDouble("key", mapOf("key" to 123.1)))
        assertEquals(null, MapUtil.toDouble("key", mapOf("key" to null)))
    }

    @Test
    fun `to Boolean`() {
        assertEquals(true, MapUtil.toBoolean("key", mapOf("key" to "true")))
        assertEquals(true, MapUtil.toBoolean("key", mapOf("key" to true)))
        assertEquals(null, MapUtil.toBoolean("key", mapOf("key" to null)))
    }

    @Test
    fun `to Map`() {
        val value = mapOf("key1" to "value1")
        assertEquals(value, MapUtil.toMap("key", mapOf("key" to value)))
        assertEquals(null, MapUtil.toMap("key", mapOf("key" to "xxx")))
        assertEquals(null, MapUtil.toMap("key", mapOf("key" to null)))
    }

    @Test
    fun `to List`() {
        val value = listOf("1", "2")
        assertEquals(value, MapUtil.toList("key", mapOf("key" to value)))
        assertEquals(null, MapUtil.toList("key", mapOf("key" to "xxx")))
        assertEquals(null, MapUtil.toList("key", mapOf("key" to null)))
    }

    @Test
    fun `applyEnv on Int`() {
        val map = mapOf("key" to 123)
        val result = MapUtil.applyEnv(map)
        assertEquals(123, result["key"])
    }

    @Test
    fun `applyEnv on String`() {
        val map = mapOf("key" to "123")
        val result = MapUtil.applyEnv(map)
        assertEquals("123", result["key"])
    }

    @Test
    fun `applyEnv on invalid environment key`() {
        val map = mapOf("key" to "\${INVALID_KEY_HOME}")
        val result = MapUtil.applyEnv(map)
        assertEquals("\${INVALID_KEY_HOME}", result["key"])
    }

    @Test
    fun `applyEnv on malformed environment key`() {
        val map = mapOf("key" to "\${INVALID_KEY_HOME")
        val result = MapUtil.applyEnv(map)
        assertEquals("\${INVALID_KEY_HOME", result["key"])
    }

    @Test
    fun `applyEnv on environment key`() {
        val map = mapOf("key" to "\${JAVA_HOME}/foo/bar")
        val result = MapUtil.applyEnv(map)
        assertNotNull(result["key"])
        assertEquals(System.getenv("JAVA_HOME") + "/foo/bar", result["key"])
    }

    @Test
    fun applyEnvOnList() {
        val map = mapOf(
            "key" to
                listOf(
                    mapOf("key1" to 123),
                    123,
                    "123",
                    "\${JAVA_HOME}",
                    listOf("1", "2"),
                )
        )
        val result = MapUtil.applyEnv(map)
        assertEquals(
            mapOf(
                "key" to
                    listOf(
                        mapOf("key1" to 123),
                        123,
                        "123",
                        System.getenv("JAVA_HOME"),
                        listOf("1", "2"),
                    )
            ),
            result
        )
    }
}

package com.wutsi.kokibot.util

import com.wutsi.kokibot.util.DurationUtil.ONE_HOUR
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DurationUtilTest {
    @Test
    fun hms() {
        val value = DurationUtil.hms(ONE_HOUR + 30 * DurationUtil.ONE_MINUTE + 15 * DurationUtil.ONE_SECOND)
        assertEquals("1h30m15s", value)
    }

    @Test
    fun ms() {
        val value = DurationUtil.hms(30 * DurationUtil.ONE_MINUTE + 15 * DurationUtil.ONE_SECOND)
        assertEquals("30m15s", value)
    }

    @Test
    fun s() {
        val value = DurationUtil.hms(15 * DurationUtil.ONE_SECOND)
        assertEquals("15s", value)
    }

    @Test
    fun `days 3d`() {
        val millis = DurationUtil.days("3d")
        assertEquals(3, millis)
    }

    @Test
    fun `minutes 3m`() {
        val minutes = DurationUtil.minutes("3m")
        assertEquals(3, minutes)
    }

    @Test
    fun `seconds 3d`() {
        val millis = DurationUtil.seconds("3s")
        assertEquals(3, millis)
    }

    @Test
    fun malformed() {
        val millis = DurationUtil.millis("Xs", -1)
        assertEquals(-1, millis)
    }

    @Test
    fun malformed2() {
        val millis = DurationUtil.millis("XxX", -1)
        assertEquals(-1, millis)
    }

    @Test
    fun `millis 1d`() {
        val millis = DurationUtil.millis("1d")
        assertEquals(86400000L, millis)
    }

    @Test
    fun `millis 3d`() {
        val millis = DurationUtil.millis("3d")
        assertEquals(259200000L, millis)
    }

    @Test
    fun `millis 3h`() {
        val millis = DurationUtil.millis("3h")
        assertEquals(10800000L, millis)
    }

    @Test
    fun `millis 30m`() {
        val millis = DurationUtil.millis("30m")
        assertEquals(1800000L, millis)
    }

    @Test
    fun `millis 30s`() {
        val millis = DurationUtil.millis("30S")
        assertEquals(30000L, millis)
    }

    @Test
    fun `millis invalid millis`() {
        val millis = DurationUtil.millis("xxx", 555)
        assertEquals(555L, millis)
    }

    @Test
    fun `millis missing millis`() {
        val millis = DurationUtil.millis("", 333)
        assertEquals(333L, millis)
    }
}

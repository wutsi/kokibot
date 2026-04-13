package com.wutsi.kokibot.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DurationUtilTest {
    @Test
    fun `millis 1d`() {
        val millis = DurationUtil.millis("1d")
        assertEquals(86400000L, millis)
    }

    @Test
    fun `millis 2d`() {
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

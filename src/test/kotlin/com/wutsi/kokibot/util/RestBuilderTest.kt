package com.wutsi.kokibot.util

import org.junit.jupiter.api.Test

class RestBuilderTest {
    @Test
    fun build() {
        RestBuilder().build(100, 1000)
    }

    @Test
    fun buildDefault() {
        RestBuilder().build(null, null)
    }
}

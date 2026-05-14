package com.wutsi.kokibot.command

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Resource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HealthCommandTest {
    private val context = mock<Context>()
    private val rs1 = mock<Resource>()
    private val rs2 = mock<Resource>()
    private val cmd = HealthCommand()

    @BeforeEach
    fun setUp() {
//        doReturn(listOf(rs1, rs2)).whenever(context).resources()
//
//        doReturn("id-1").whenever(rs1).id()
//        doReturn("id-2").whenever(rs2).id()
    }

    @Test
    fun metadata() {
        assertEquals("/health", cmd.metadata().name)
    }

    @Test
    fun `exec - up`() {
        doReturn(
            Health(
                id = "-",
                up = true,
                children = listOf(
                    Health(id = "id-1", up = true),
                    Health(id = "id-2", up = true)
                )
            )
        ).whenever(context).health()

        val result = cmd.exec(Message(text = ""), context)

        assertEquals(
            """
                Overall Health: ✅

                ✅ `id-1`
                ✅ `id-2`
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `exec - down`() {
//        doReturn(Health(id = "id-1", up = true)).whenever(rs1).health()
//        doReturn(Health(id = "id-2", up = true)).whenever(rs2).health()
        doReturn(
            Health(
                id = "-",
                up = false,
                children = listOf(
                    Health(id = "id-1", up = true),
                    Health(id = "id-2", up = false)
                )
            )
        ).whenever(context).health()

        val result = cmd.exec(Message(text = ""), context)

        assertEquals(
            """
                Overall Health: ❌

                ✅ `id-1`
                ❌ `id-2`
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `exec - resource`() {
        doReturn(listOf(rs1, rs2)).whenever(context).resources()
        doReturn("id-1").whenever(rs1).id()
        doReturn("id-2").whenever(rs2).id()
        doReturn(
            Health(
                id = "-",
                up = false,
                details = "Something went wrong",
            )
        ).whenever(rs2).health()

        val result = cmd.exec(Message(text = "id-2"), context)

        assertEquals(
            """
                ❌ `id-2`

                Something went wrong
            """.trimIndent(),
            result
        )
    }

    @Test
    fun `exec - resource not found`() {
        doReturn(listOf(rs1, rs2)).whenever(context).resources()
        doReturn("id-1").whenever(rs1).id()
        doReturn("id-2").whenever(rs2).id()

        val result = cmd.exec(Message(text = "xxx"), context)

        assertEquals("Resource not found: `xxx`", result)
    }
}

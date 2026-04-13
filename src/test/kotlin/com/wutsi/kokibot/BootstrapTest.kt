package com.wutsi.kokibot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.File

class BootstrapTest {
    private val contextFactory = mock<ContextFactory>()
    private val bootstrap = Bootstrap(contextFactory)

    private val context = mock<Context>()

    @BeforeEach
    fun setup() {
        doReturn(Health(id = "-")).whenever(context).health()
        doReturn(context).whenever(contextFactory).create(any(), any())
    }

    @Test
    fun destroy() {
        bootstrap.init(getResourceFile("/home/007"))

        bootstrap.destroy()

        verify(context).destroy()
    }

    @Test
    fun init() {
        val home = getResourceFile("/home/007")
        bootstrap.init(home)

        verify(context).init(any(), any())
    }

    private fun getResourceFile(path: String): File {
        val resource = BootstrapTest::class.java.getResource(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return File(resource.toURI())
    }
}

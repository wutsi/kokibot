package com.wutsi.kokibot.controller

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.kokibot.Assistant
import com.wutsi.kokibot.Bootstrap
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.service.inbox.Inbox
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class QueryCancelControllerTest {
    @MockitoBean
    private lateinit var multi: MultiBootstrap

    @Autowired
    protected lateinit var rest: TestRestTemplate

    private val inbox = mock<Inbox>()

    @BeforeEach
    fun setup() {
        val assistant = mock<Assistant>()
        doReturn("my-agent").whenever(assistant).name

        val context = mock<Context>()
        doReturn(assistant).whenever(context).assistant
        doReturn(inbox).whenever(context).inbox

        val bootstrap = mock<Bootstrap>()
        doReturn(context).whenever(bootstrap).getContext()

        doReturn(bootstrap).whenever(multi).get("my-agent")
    }

    @Test
    fun `cancel returns 200 and requests cancellation`() {
        val response = rest.postForEntity(
            "/assistants/my-agent/queries/query-1/cancel",
            null,
            Any::class.java,
        )

        assertEquals(200, response.statusCode.value())
        verify(inbox).cancel("query-1")
    }

    @Test
    fun `cancel returns 404 when assistant not found`() {
        val response = rest.postForEntity(
            "/assistants/unknown/queries/query-1/cancel",
            null,
            Any::class.java,
        )

        assertEquals(404, response.statusCode.value())
    }
}

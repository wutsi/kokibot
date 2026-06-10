package com.wutsi.kokibot.llm.none

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.llm.LLM
import com.wutsi.kokibot.llm.LLMBalance
import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMRequest
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMStreamChunk
import com.wutsi.kokibot.tools.Tool

class NullLLM : LLM {
    companion object {
        const val MESSAGE = "This is a null LLM. It does not generate any response."
    }

    override fun model(): String {
        return "-"
    }

    override fun name(): String {
        return "null"
    }

    override fun init(config: Map<*, *>, context: Context) {
    }

    override fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse {
        return LLMResponse(
            id = "",
            choices = listOf(
                LLMResponseChoice(
                    content = MESSAGE,
                    finishReason = LLMFinishReason.STOP,
                )
            ),
        )
    }

    override fun balance(): LLMBalance? {
        return null
    }

    override fun completionStream(
        request: LLMRequest,
        tools: List<Tool>,
        onChunk: (LLMStreamChunk) -> Unit,
    ): LLMResponse {
        return completion(request, tools)
    }

    override fun health(): Health {
        return Health(up = false, id = id())
    }

    override fun maxContextWindow(): Int {
        return -1
    }
}

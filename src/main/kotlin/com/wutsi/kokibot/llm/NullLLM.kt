package com.wutsi.kokibot.llm

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Health
import com.wutsi.kokibot.tools.Tool

class NullLLM : LLM {
    companion object {
        const val MESSAGE = "This is a null LLM. It does not generate any response."
    }

    override fun id(): String {
        return "llm"
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

    override fun health(): Health {
        return Health(up = false, id = id())
    }
}

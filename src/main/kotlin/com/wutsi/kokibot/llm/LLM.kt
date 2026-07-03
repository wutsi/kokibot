package com.wutsi.kokibot.llm

import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.tools.Tool

interface LLM : Resource {
    override fun destroy() {
    }

    override fun id(): String {
        return "llm:" + getName()
    }

    fun supportsStreaming(): Boolean {
        return false
    }

    fun getName(): String

    fun getModel(): String

    fun getReasoningEffort(): String?

    /**
     * Returns the maximum context length (in tokens) that this LLM can handle.
     * This is used to determine how much of the conversation history and tool outputs can be included
     * in the prompt when calling the LLM.
     */
    fun getMaxContextWindow(): Int

    /**
     * Synchronous completion (existing method, unchanged).
     */
    fun completion(request: LLMRequest, tools: List<Tool>): LLMResponse

    /**
     * Streaming completion with callback for incremental chunks.
     *
     * @param request The LLM request with prompt and system instructions
     * @param tools Available tools for function calling
     * @param onChunk Callback invoked for each stream chunk
     * @return Complete LLMResponse after all chunks received
     */
    fun completionStream(
        request: LLMRequest,
        tools: List<Tool>,
        onChunk: (LLMStreamChunk) -> Unit,
    ): LLMResponse

    fun balance(): LLMBalance?

    fun availableModels(): List<String>
}

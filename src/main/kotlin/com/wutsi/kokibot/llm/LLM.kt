package com.wutsi.kokibot.llm

import com.wutsi.kokibot.Resource
import com.wutsi.kokibot.tools.Tool

interface LLM : Resource {
    override fun destroy() {
    }

    fun supportsStreaming(): Boolean {
        return false
    }

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
}

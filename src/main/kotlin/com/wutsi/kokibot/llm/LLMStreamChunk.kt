package com.wutsi.kokibot.llm

/**
 * Represents an incremental chunk received from a streaming LLM response.
 * Used during SSE (Server-Sent Events) streaming to deliver partial results.
 */
data class LLMStreamChunk(
    /**
     * Incremental text content delta.
     * Example: First chunk "Hello", second chunk " world" → accumulates to "Hello world"
     */
    val delta: String? = null,

    /**
     * Incremental reasoning content delta (DeepSeek V4/R1 only).
     * Contains the model's thinking process before generating the final answer.
     */
    val reasoningDelta: String? = null,

    /**
     * Complete tool call when it's fully streamed.
     * Only present when the LLM requests a tool execution and all JSON is received.
     */
    val toolCall: LLMToolCall? = null,

    /**
     * Incremental tool-call fragment (OpenAI-compatible streaming).
     * Tool calls arrive across several chunks and must be merged by [LLMToolCallDelta.index].
     */
    val toolCallDelta: LLMToolCallDelta? = null,

    /**
     * Finish reason when the stream is complete.
     */
    val finishReason: LLMFinishReason? = null,

    /**
     * Indicates if this is the final chunk in the stream.
     */
    val isDone: Boolean = false,
)

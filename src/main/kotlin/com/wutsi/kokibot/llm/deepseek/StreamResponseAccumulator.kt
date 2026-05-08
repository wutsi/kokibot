package com.wutsi.kokibot.llm.deepseek

import com.wutsi.kokibot.llm.LLMFinishReason
import com.wutsi.kokibot.llm.LLMResponse
import com.wutsi.kokibot.llm.LLMResponseChoice
import com.wutsi.kokibot.llm.LLMStreamChunk
import com.wutsi.kokibot.llm.LLMUsage
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/**
 * Accumulates streaming chunks into a complete [LLMResponse].
 *
 * Tool calls require special handling: in OpenAI-compatible streaming a single
 * tool call is delivered across multiple chunks. The first chunk carries the
 * `id` and function `name`; subsequent chunks contain only fragments of the
 * JSON `arguments`. All fragments sharing the same `index` must be concatenated
 * and parsed once, after the stream has finished.
 */
class StreamResponseAccumulator(private val jsonMapper: JsonMapper) {
    private val contentBuilder = StringBuilder()
    private val reasoningContentBuilder = StringBuilder()

    /** Tool call accumulators keyed by the provider's tool-call `index`. */
    private val toolCallAccumulators = linkedMapOf<Int, ToolCallAccumulator>()

    private var finishReason: LLMFinishReason? = null
    private val responseId: String = UUID.randomUUID().toString()
    private var usage: LLMUsage? = null

    fun add(chunk: LLMStreamChunk) {
        chunk.delta?.let { contentBuilder.append(it) }
        chunk.reasoningDelta?.let { reasoningContentBuilder.append(it) }
        chunk.finishReason?.let { finishReason = it }

        // Streaming tool-call deltas — merge by index.
        chunk.toolCallDelta?.let { delta ->
            val acc = toolCallAccumulators.getOrPut(delta.index) { ToolCallAccumulator(jsonMapper) }
            acc.merge(delta)
        }

        // Already-complete tool call (rare in streaming; supported for safety).
        chunk.toolCall?.let { complete ->
            val nextIndex = (toolCallAccumulators.keys.maxOrNull() ?: -1) + 1
            toolCallAccumulators[nextIndex] = ToolCallAccumulator(jsonMapper).apply { setComplete(complete) }
        }

        this.usage = chunk.usage
    }

    fun toResponse(): LLMResponse {
        val toolCalls = toolCallAccumulators.values.mapNotNull { it.build(jsonMapper) }
        return LLMResponse(
            id = responseId,
            usage = usage,
            choices = listOf(
                LLMResponseChoice(
                    index = 0,
                    finishReason = finishReason,
                    content = contentBuilder.toString().ifEmpty { null },
                    reasoningContent = reasoningContentBuilder.toString().ifEmpty { null },
                    toolCalls = toolCalls,
                )
            )
        )
    }
}

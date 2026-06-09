package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message

/**
 * Interface for reasoning loop strategies.
 * Different implementations can provide different reasoning approaches:
 * - ReAct (Reasoning + Acting)
 * - Chain-of-Thought
 * - Tree-of-Thoughts
 * - Reflexion
 * etc.
 */
interface ReasoningLoop {
    /**
     * Execute the reasoning loop to process a query.
     *
     * @param query The user query to process
     * @param streamCallback Optional callback for streaming responses
     * @param startIteration Starting iteration number (for resumed sessions)
     * @param memory Mutable list of iteration memory (reasoning steps and observations)
     * @param context The execution context
     * @return The final response message
     */
    fun execute(
        query: Message,
        streamCallback: ((String) -> Unit)?,
        startIteration: Int,
        memory: MutableList<String>,
        context: Context
    ): Message
}

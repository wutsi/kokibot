package com.wutsi.kokibot.llm

/**
 * Represents an incremental fragment of a tool call received during streaming.
 *
 * In OpenAI-compatible streaming (DeepSeek, Kimi, etc.), tool calls arrive
 * as multiple partial deltas:
 *  - The first delta typically contains the [index], [id] and function [name]
 *  - Subsequent deltas carry only fragments of the JSON [argumentsFragment]
 *
 * Fragments share the same [index] and must be concatenated (in arrival order)
 * to reconstruct the full JSON arguments string before parsing.
 */
data class LLMToolCallDelta(
    /** Position of the tool call inside the choice (used to merge fragments). */
    val index: Int = 0,

    /** Provider-assigned tool call id (only present in the first delta). */
    val id: String? = null,

    /** Function name (only present in the first delta). */
    val name: String? = null,

    /** Raw JSON fragment of the arguments. Not necessarily valid JSON on its own. */
    val argumentsFragment: String? = null,
)

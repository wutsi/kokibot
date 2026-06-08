package com.wutsi.kokibot.tools.user

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.llm.LLMToolCall
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType

class UserAskQuestionTool : Tool {
    companion object {
        const val ID: String = "user_ask"
    }

    private lateinit var context: Context

    override fun id(): String = ID

    override fun init(config: Map<*, *>, context: Context) {
        super.init(config, context)
        this.context = context
    }

    override fun metadata(): ToolMetadata {
        return ToolMetadata(
            name = ID,
            description = """
                This tool is used to ask a question to the user. It will pause the current session and wait for the user's response before resuming.
            """.trimIndent(),
            parameters = listOf(
                ToolParameter(
                    name = "question",
                    type = ToolParameterType.STRING,
                    description = "Question to ask to the user. Be clear and specific.",
                    required = true,
                )
            )
        )
    }

    override fun statusText(toolCalls: List<LLMToolCall>): String {
        return "Asking question to user"
    }

    override fun exec(arguments: Map<*, *>): String {
        // Question
        val question = arguments["question"]?.toString()?.ifEmpty { null }
            ?: throw IllegalArgumentException("Missing required argument: question")

        // Throw the exception to signal the assistant to pause and wait for user input
        throw AskQuestionException(question)
    }
}

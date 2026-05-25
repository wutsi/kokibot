package com.wutsi.kokibot.tools.swarm

import com.wutsi.kokibot.AssistantNotFoundException
import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.Role
import com.wutsi.kokibot.service.SessionContext
import com.wutsi.kokibot.service.swarm.DelegationException
import com.wutsi.kokibot.tools.Tool
import com.wutsi.kokibot.tools.ToolMetadata
import com.wutsi.kokibot.tools.ToolParameter
import com.wutsi.kokibot.tools.ToolParameterType
import org.slf4j.LoggerFactory

class SwarmDelegateTool : Tool {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SwarmDelegateTool::class.java)

        const val ID: String = "swarm_delegate"
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
                Delegate a task to a specialist agent for expert handling.
                The specialist agent will:
                  - Process the task using their specialized tools and skills
                  - Apply their domain expertise
                  - Return detailed results

                Use this when a task requires specialized knowledge or capabilities
                that a particular agent is best suited to handle.
            """.trimIndent(),
            parameters = listOf(
                ToolParameter(
                    name = "name",
                    type = ToolParameterType.STRING,
                    description = "Name of the specialist agent to delegate to. Be sure to use the exact name as registered.",
                    required = true,
                ),
                ToolParameter(
                    name = "task",
                    type = ToolParameterType.STRING,
                    description = "The specific task or question to delegate. Be clear and specific.",
                    required = true,
                ),
                ToolParameter(
                    name = "context",
                    type = ToolParameterType.STRING,
                    description = "Optional additional context or constraints for this task",
                    required = false,
                ),
            )
        )
    }

    override fun exec(arguments: Map<*, *>): String {
        val name = arguments["name"] as? String ?: throw IllegalArgumentException("Missing required parameter: name")
        val task = arguments["task"] as? String ?: throw IllegalArgumentException("Missing required parameter: task")
        val taskContext = (arguments["context"] as? String?)?.ifEmpty { null }

        // Get session ID from thread-local context
        val sessionId = SessionContext.getSessionId()
            ?: return "Error: Cannot determine session context for delegation"

        try {
            // Get current stream callback to propagate to delegated assistant
            val currentCallback = context.delegationStack.getCurrentStreamCallback(sessionId)

            // Execute delegation
            val assistant = context.assistantRegistry.get(name)
            val result = assistant.process(
                Message(
                    id = sessionId,
                    role = Role.USER,
                    text = task + (taskContext?.let { "\n\nAdditional context:\n$it" } ?: ""),
                    userId = "tool:$ID",
                    channelId = "internal"
                ),
                currentCallback
            )
            return "Result from `$name`:\n${result.text}"
        } catch (e: DelegationException) {
            // Return validation error to LLM
            return "Error: ${e.message}"
        } catch (_: AssistantNotFoundException) {
            return "Error: Specialist agent '$name' not found. Please check the name and try again."
        } catch (e: Exception) {
            LOGGER.error("Error delegating task to '$name'", e)
            return "Error delegating task to '$name': ${e.message}"
        }
    }
}

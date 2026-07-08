package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.service.kb.KBEntryStatus
import com.wutsi.kokibot.service.memory.ConversationMessage
import org.apache.commons.io.IOUtils
import java.io.File
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PromptBuilder(
    private val clock: Clock = Clock.systemDefaultZone()
) {
    companion object {
        private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "EEEE, MMMM d, yyyy 'at' HH:mm:ss zzz",
            Locale.ENGLISH
        )
    }

    fun buildPrompt(query: Message, iterationMemory: List<String>, context: Context): String {
        val sb = StringBuilder()

        // Add Tracking information
        val now = ZonedDateTime.now(clock)
        sb.append("# Tracking Information\n")
        sb.append("Current Date and Time: ${now.format(DATE_TIME_FORMATTER)} (${now.toOffsetDateTime()})\n")
        sb.append("Home Directory: ${context.home}\n")
        sb.append("Session ID: ${query.id}\n")
        query.conversationId?.let { sb.append("Conversation ID: ${query.conversationId}\n") }

        // Conversation History
        val conversationMessages = loadConversationMessages(query, context)
        if (conversationMessages.isNotEmpty()) {
            sb.append("\n---\n")
            sb.append("# Conversation History\n")
            conversationMessages.forEach { msg ->
                sb.append("<${msg.role}>\n(${msg.dateTime}):\n${msg.text}\n</${msg.role}>\n")
            }
        }
        sb.append("<user>\n${query.text}\n</user>\n")

        query.channelId?.let { channelId ->
            loadChannelInstructions(channelId)?.let { channelInstructions ->
                sb.append("\n---\n")
                sb.append("# Formatting Instructions\n")
                sb.append("<instructions>\n$channelInstructions\n</instructions>\n")
            }
        }

        if (context.memory.isEnabled()) {
            val longTermMemory = context.memory.get()
            if (longTermMemory != null) {
                sb.append("\n---\n")
                sb.append("# Long-Term Memory\n")
                sb.append("Here are information that you have stored in your long-term memory in Markdown format:\n")
                sb.append("<memory>\n$longTermMemory\n</memory>\n")
            }

            val shortTermMemory = context.dailyLog.get()
            if (shortTermMemory != null) {
                sb.append("\n---\n\n")
                sb.append("# Short-Term Memory\n")
                sb.append("Here are information that you have stored in your short-term memory in Markdown format:\n")
                sb.append("<memory>\n$shortTermMemory\n</memory>\n")
            }
        }

        if (iterationMemory.isNotEmpty()) {
            sb.append("\n---\n\n")
            sb.append("# Previous reasoning steps and observations\n")
            iterationMemory.forEach { line -> sb.append("<observation>\n$line\n</observation>\n") }
        }

        return applyVariables(sb.toString(), query, context)
    }

    fun buildSystemInstructions(
        query: Message,
        context: Context
    ): String {
        val entries = listOfNotNull(
            loadInstructions(context),
            identityInstructions(context),
//            coordinatorInstructions(),
            dailyLogInstructions(context),
            knowledgeBaseInstructions(context),
            skillsInstructions(context),
            mcpInstructions(context),
            securityInstructions(),
        )
        return applyVariables(entries.joinToString("\n\n---\n\n"), query, context)
    }

    internal fun loadInstructions(context: Context): String? {
        return loadInstructions(context.home)
    }

    internal fun saveInstructions(content: String, context: Context) {
        val file = File(context.home, "ASSISTANT.md")
        file.writeText(content)
    }

    private fun loadInstructions(home: File): String? {
        val file = File(home, "ASSISTANT.md")
        return if (file.exists()) {
            file.readText()
        } else {
            null
        }
    }

    private fun loadConversationMessages(query: Message, context: Context): List<ConversationMessage> {
        val conversationId = query.conversationId ?: return emptyList()
        val userId = query.userId ?: return emptyList()
        val channelId = query.channelId ?: return emptyList()
        return context.conversationRepository.getMessages(conversationId, userId, channelId)
    }

    private fun skillsInstructions(context: Context): String? {
        val skills = context.skillRegistry
            .all()
            .filter { skill -> skill.health().up }
            .joinToString("\n") { skill ->
                listOfNotNull(
                    "## Skill: ${skill.metadata.name}\n\n" +
                        "**Home Directory:** ${skill.metadata.home}\n\n" +
                        "**Description:** ${skill.metadata.description}"
                ).joinToString("\n\n")
            }
            .ifEmpty { null }

        return skills?.let { "# Available skills\n\nHere are the skills available:\n\n$skills" }
    }

    private fun mcpInstructions(context: Context): String? {
        val servers = context.mcpRegistry.all()
        if (servers.isEmpty()) return null

        val lines = servers.joinToString("\n") { server ->
            "## ${server.config.name}\n\n**Description:** ${server.config.description}"
        }
        return "# Available MCP Servers\n\nActivate with `mcp_activate`:\n\n$lines"
    }

    private fun identityInstructions(context: Context): String? {
        val assistant = context.assistant
        val lines = listOfNotNull(
            "- **Assistant Handle:** ${assistant.name}",
            assistant.getFullName().ifEmpty { null }?.let { "- **User Full Name:** $it" },
            assistant.getEmail().ifEmpty { null }?.let { "- **User Email:** $it" },
        )
        return "# Assistant Identity\n\n${lines.joinToString("\n")}"
    }

    private fun securityInstructions(): String {
        return IOUtils.toString(
            javaClass.getResource("/instructions/SECURITY.md"),
            "utf-8"
        )
    }

    private fun coordinatorInstructions(): String {
        return IOUtils.toString(
            javaClass.getResource("/instructions/COORDINATOR.md"),
            "utf-8"
        )
    }

    private fun dailyLogInstructions(context: Context): String? {
        return if (context.memory.isEnabled()) {
            IOUtils.toString(
                javaClass.getResourceAsStream("/instructions/DAILY_LOG.md"),
                "utf-8"
            )
        } else {
            null
        }
    }

    private fun knowledgeBaseInstructions(context: Context): String? {
        val kb = context.knowledgeBase
        if (!kb.isEnabled()) {
            return null
        }

        val entries = kb.entries().filter { entry -> entry.status == KBEntryStatus.READY }
        if (entries.isEmpty()) {
            return null
        }

        val content = entries.joinToString("\n\n") { entry ->
            listOfNotNull(
                "### ${entry.name}",
                entry.scope?.ifEmpty { null }?.let { "**Scope:** ${entry.scope}" },
                entry.keywords.ifEmpty { null }?.let { "**Keywords:** ${entry.keywords.joinToString(", ")}" },
                entry.summary?.let { path -> "**Summary File:** {{HOME}}/$path" },
                entry.raw?.let { path -> "**Raw File:** {{HOME}}/$path" },
            ).joinToString("\n")
        }

        val usage = listOfNotNull(
            if (kb.isExclusive()) {
                """
                - Use only your knowledge base.
                - Do not use your own training knowledge to answer questions.
                - Do not make up information that is not in your knowledge base.
            """.trimIndent()
            } else {
                "- Prefer your knowledge base content over your own training knowledge, but you can use your own training knowledge if the answer is not in your knowledge base."
            },

            if (!kb.isWebSearch()) {
                """
                - Do not use web search to answer questions
                """.trimIndent()
            } else {
                null
            },

            "- If you don't know the answer, say \"I don't know\" or \"I don't have enough information to answer that question\"",
        ).joinToString("\n")

        return "# Knowledge Base Instructions\n\n" +
            "## Knowledge Base Content\n\nHere are the knowledge base entries available:\n\n$content\n" +
            "## Knowledge Base Usage Instructions\n\n$usage"
    }

    private fun loadChannelInstructions(channelId: String): String? {
        val input = javaClass.getResourceAsStream("/instructions/channel/$channelId.md") ?: return null
        return IOUtils.toString(input, "utf-8")
    }

    private fun applyVariables(text: String, query: Message, context: Context): String {
        val userId = query.userId ?: "-"
        val channelId = query.channelId?.removePrefix("channel:") ?: "-"

        return text.replace("{{ASSISTANT_NAME}}", context.assistant.name)
            .replace("{{HOME}}", context.home.absolutePath)
            .replace("{{USER_ID}}", userId)
            .replace("{{CHANNEL_ID}}", channelId.removePrefix("channel:"))
    }
}

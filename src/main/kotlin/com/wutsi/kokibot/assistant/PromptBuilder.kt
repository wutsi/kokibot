package com.wutsi.kokibot.assistant

import com.wutsi.kokibot.Context
import com.wutsi.kokibot.Message
import com.wutsi.kokibot.service.memory.ConversationMessage
import org.apache.commons.io.IOUtils
import java.io.File
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PromptBuilder(
    private val assistantName: String,
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
        val text = buildText(query, context)
        sb.append("Query: $text\n")

        sb.append("\n---\n")
        sb.append("# Current Date and Time\n")
        val now = ZonedDateTime.now(clock)
        sb.append("The current date and time is: ${now.format(DATE_TIME_FORMATTER)} (${now.toOffsetDateTime()})\n")

        val conversationMessages = loadConversationMessages(query, context)
        if (conversationMessages.isNotEmpty()) {
            sb.append("\n---\n")
            sb.append("# Conversation History\n")
            conversationMessages.forEach { msg ->
                sb.append("<${msg.role}>\n(${msg.dateTime}):\n${msg.text}\n</${msg.role}>\n")
            }
        }

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

        if (iterationMemory.isNotEmpty()) {
            sb.append("\n---\n\n")
            sb.append("# Previous reasoning steps and observations\n")
            iterationMemory.forEach { line -> sb.append("<observation>\n$line\n</observation>\n") }
        }

        return applyVariables(sb.toString(), query, context)
    }

    fun buildSystemInstructions(
        query: Message,
        coordinator: Boolean,
        context: Context
    ): String {
        val entries = listOfNotNull(
            loadIdentity(context),
            if (coordinator) coordinatorInstructions() else null,
            dailyLogInstructions(),
            skillsInstructions(context),
            mcpInstructions(context),
            securityInstructions(),
        )
        return applyVariables(entries.joinToString("\n\n---\n\n"), query, context)
    }

    internal fun buildText(query: Message, context: Context): String {
        query.channelId ?: return query.text

        val channelId = query.channelId.removePrefix("channel:")

        val input = javaClass.getResourceAsStream("/instructions/channel/$channelId.md") ?: return query.text
        return query.text +
            "\n\n" +
            IOUtils.toString(input, "utf-8")
    }

    internal fun loadIdentity(context: Context): String? {
        return loadIdentity(context.home)
    }

    internal fun saveIdentity(content: String, context: Context) {
        val file = File(context.home, "ASSISTANT.md")
        file.writeText(content)
    }

    private fun loadIdentity(home: File): String? {
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

    private fun dailyLogInstructions(): String {
        return IOUtils.toString(
            javaClass.getResourceAsStream("/instructions/DAILY_LOG.md"),
            "utf-8"
        )
    }

    private fun applyVariables(text: String, query: Message, context: Context): String {
        val userId = query.userId ?: "-"
        val channelId = query.channelId?.removePrefix("channel:") ?: "-"

        return text.replace("{{ASSISTANT_NAME}}", assistantName)
            .replace("{{HOME}}", context.home.absolutePath)
            .replace("{{USER_ID}}", userId)
            .replace("{{CHANNEL_ID}}", channelId.removePrefix("channel:"))
    }
}
